package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.provider.NamedProviderSource;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.VideoProviders;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.ScreenControl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

public class GroupPacketHandler {
    private static final Gson gson = new Gson();

    public static void handle(String json) {
        LOGGER.info("Group message: {}", json);
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String type = string(object, "type");
            JsonObject payload = payload(object);
            switch (type) {
                case "hello_ack" -> helloAck();
                case "room_created" -> roomCreated(payload);
                case "room_list" -> roomList(payload);
                case "room_state" -> roomState(payload, object);
                case "screen_bound" -> screenBound(payload);
                case "screen_unbound" -> screenUnbound();
                case "member_joined" -> memberJoined(payload);
                case "member_left" -> memberLeft(payload);
                case "room_disbanded" -> roomDisbanded(payload);
                case "play" -> play(payload);
                case "stop" -> stop();
                case "pause" -> pause(payload);
                case "seek" -> seek(payload);
                case "sync_state" -> syncState(payload);
                case "playlist_update" -> playlistUpdate(payload);
                case "skip", "play_next" -> playNext(payload);
                case "error" -> error(object);
                default -> LOGGER.info("Unknown group message type: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle group message", e);
            message("无法解析房间服务器消息", Formatting.RED);
        }
    }

    private static void helloAck() {
        message("房间服务器握手成功", Formatting.GREEN);
    }

    private static void roomCreated(JsonObject object) {
        message("房间已创建: " + stringValue(object, "name", "roomName") + " (" + string(object, "roomId") + ")", Formatting.GREEN);
    }

    private static void roomList(JsonObject object) {
        JsonArray rooms = object.has("rooms") && object.get("rooms").isJsonArray() ? object.getAsJsonArray("rooms") : new JsonArray();
        if (rooms.isEmpty()) {
            message("当前没有可加入的房间", Formatting.GOLD);
            return;
        }
        String text = rooms.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .peek(GroupPacketHandler::syncCurrentRoomFromList)
                .map(room -> "%s - %s".formatted(string(room, "roomId"), stringValue(room, "name", "roomName")))
                .collect(Collectors.joining("\n"));
        message("房间列表:\n" + text, Formatting.GOLD);
    }

    private static void roomState(JsonObject object, JsonObject envelope) {
        GroupRoomState state = object.has("state") && object.get("state").isJsonObject()
                ? gson.fromJson(object.get("state"), GroupRoomState.class)
                : gson.fromJson(object, GroupRoomState.class);
        JsonObject stateObject = object.has("state") && object.get("state").isJsonObject() ? object.getAsJsonObject("state") : object;
        if (state.roomName == null || state.roomName.isEmpty()) state.roomName = stringValue(object, "name", "roomName");
        if (state.hostName == null || state.hostName.isEmpty()) state.hostName = string(object, "hostName");
        normalizeClockState(state, stateObject);
        if (state.seq == 0) state.seq = longValue(envelope, "seq", 0);
        MinecraftClient client = MinecraftClient.getInstance();
        GroupClient.updateState(state, client.player == null ? "" : client.player.getUuidAsString());
        if (state.hostScreen != null) {
            GroupClient.onHostScreenReceived(state.hostScreen);
        } else if (GroupClient.usingHostScreen) {
            GroupClient.onHostScreenUnbound();
        }
        if (GroupClient.hasPlayableBoundScreen()) GroupClient.restoreCurrentVideoToBoundScreen();
        message("已更新房间状态: " + GroupClient.roomName + "，成员 " + GroupClient.getMemberCount() + " 人", Formatting.GREEN);
    }

    private static void screenBound(JsonObject object) {
        ScreenDescriptor screen = screenDescriptor(object);
        if (screen == null) return;
        if (GroupClient.state != null) GroupClient.state.hostScreen = screen;
        GroupClient.onHostScreenReceived(screen);
    }

    private static void screenUnbound() {
        GroupClient.onHostScreenUnbound();
    }

    private static void memberJoined(JsonObject object) {
        String uuid = string(object, "uuid");
        String name = string(object, "name");
        GroupClient.upsertMember(uuid, name);
        message("成员加入房间: " + name, Formatting.GREEN);
    }

    private static void memberLeft(JsonObject object) {
        String uuid = string(object, "uuid");
        String name = string(object, "name");
        GroupClient.removeMember(uuid, name);
        message("成员离开房间: " + name, Formatting.YELLOW);
    }

    private static void roomDisbanded(JsonObject object) {
        GroupClient.clearRoom();
        message("房间已解散", Formatting.YELLOW);
    }

    private static void play(JsonObject object) {
        if (!object.has("video") || !object.get("video").isJsonObject()) return;
        VideoInfo info = gson.fromJson(object.get("video"), VideoInfo.class);
        long progress = longValue(object, "progress", 0);
        if (info.expire() > 0 && System.currentTimeMillis() > info.expire() && info.rawPath() != null && !info.rawPath().isEmpty()) {
            CompletableFuture<VideoInfo> future = VideoProviders.from(info.rawPath(), new NamedProviderSource(info.playerName()));
            if (future != null) {
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        LOGGER.error(e.toString());
                        return null;
                    }
                }).thenAccept(resolved -> MinecraftClient.getInstance().execute(() -> playBound(resolved == null ? info : resolved, progress)));
                return;
            }
        }
        playBound(info, progress);
    }

    private static void stop() {
        if (GroupClient.state != null) {
            GroupClient.state.currentVideo = null;
            GroupClient.state.currentProgress = 0;
            GroupClient.state.progress = 0;
            GroupClient.state.paused = false;
            GroupClient.state.receivedAt = System.currentTimeMillis();
        }
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null) return;
        GroupClient.showCurrentVideo(screen, null);
        ScreenControl.stop(screen);
    }

    private static void pause(JsonObject object) {
        boolean paused = !object.has("paused") || object.get("paused").getAsBoolean();
        long progress = longValue(object, "progress", GroupClient.state == null ? 0 : GroupClient.state.estimateProgress());
        if (GroupClient.state != null) {
            GroupClient.state.paused = paused;
            GroupClient.state.progress = Math.max(progress, 0);
            GroupClient.state.currentProgress = GroupClient.state.progress;
            GroupClient.state.rate = floatValue(object, "rate", GroupClient.state.rate <= 0 ? 1f : GroupClient.state.rate);
            GroupClient.state.serverTime = longValue(object, "serverTime", GroupClient.state.serverTime);
            GroupClient.state.receivedAt = System.currentTimeMillis();
        }
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null || screen.player == null) return;
        ScreenControl.pause(screen, paused, progress);
    }

    private static void seek(JsonObject object) {
        long progress = longValue(object, "progress", 0);
        if (GroupClient.state != null) {
            GroupClient.state.progress = Math.max(progress, 0);
            GroupClient.state.currentProgress = GroupClient.state.progress;
            GroupClient.state.receivedAt = System.currentTimeMillis();
        }
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null || screen.player == null) return;
        ScreenControl.seek(screen, progress);
    }

    private static void syncState(JsonObject object) {
        GroupSyncManager.applySync(
                longValue(object, "progress", 0),
                longValue(object, "baseProgress", longValue(object, "progress", 0)),
                longValue(object, "baseServerTime", 0),
                longValue(object, "serverTime", longValue(object, "clientTime", System.currentTimeMillis())),
                object.has("paused") && object.get("paused").getAsBoolean(),
                floatValue(object, "rate", 1f)
        );
    }

    private static void playNext(JsonObject object) {
        if (object.has("video") && object.get("video").isJsonObject()) {
            play(object);
            return;
        }
        stop();
    }

    private static void playlistUpdate(JsonObject object) {
        ArrayList<GroupPlaylistItem> playlist = new ArrayList<>();
        JsonArray array = object.has("playlist") && object.get("playlist").isJsonArray() ? object.getAsJsonArray("playlist") : new JsonArray();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                playlist.add(gson.fromJson(element, GroupPlaylistItem.class));
            }
        }
        if (GroupClient.state == null) GroupClient.state = new GroupRoomState();
        GroupClient.state.playlist = playlist;
        updateBoundPlaylist();
        message("群组队列已更新，共 " + playlist.size() + " 项", Formatting.GREEN);
    }

    private static void updateBoundPlaylist() {
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null || GroupClient.state == null || GroupClient.state.playlist == null) return;
        VideoInfo[] infos = GroupClient.state.playlist.stream()
                .map(GroupPacketHandler::videoInfo)
                .filter(Objects::nonNull)
                .toArray(VideoInfo[]::new);
        ScreenControl.updatePlaylist(screen, infos);
    }

    private static VideoInfo videoInfo(GroupPlaylistItem item) {
        if (item.video != null) return item.video;
        if (item.url == null) return null;
        return new VideoInfo(item.requesterName == null ? "" : item.requesterName, item.name == null ? item.url : item.name, item.url, item.url, -1, false, new String[0]);
    }

    private static void playBound(VideoInfo info, long progress) {
        GroupSyncManager.setCurrentVideo(info, progress);
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null) return;
        GroupClient.showCurrentVideo(screen, info);
        ScreenControl.play(screen, info, GroupClient.state == null ? progress : GroupClient.state.estimateProgress());
    }

    private static void error(JsonObject object) {
        JsonObject payload = object.has("payload") && object.get("payload").isJsonObject() ? object.getAsJsonObject("payload") : object;
        String code = string(payload, "code");
        String text = string(payload, "message");
        message("房间服务器错误: " + (code.isEmpty() ? "" : code + " ") + text, Formatting.RED);
    }

    private static ScreenDescriptor screenDescriptor(JsonObject object) {
        if (object.has("screen") && object.get("screen").isJsonObject()) {
            return gson.fromJson(object.get("screen"), ScreenDescriptor.class);
        }
        if (object.has("hostScreen") && object.get("hostScreen").isJsonObject()) {
            return gson.fromJson(object.get("hostScreen"), ScreenDescriptor.class);
        }
        return object.isJsonObject() && object.has("areaName") ? gson.fromJson(object, ScreenDescriptor.class) : null;
    }

    private static void normalizeClockState(GroupRoomState state, JsonObject object) {
        long progress = longValue(object, "progress", state.progress > 0 ? state.progress : state.currentProgress);
        state.progress = Math.max(progress, 0);
        state.currentProgress = state.progress;
        state.baseProgress = longValue(object, "baseProgress", state.progress);
        state.baseServerTime = longValue(object, "baseServerTime", state.baseServerTime);
        state.serverTime = longValue(object, "serverTime", state.serverTime);
        state.rate = floatValue(object, "rate", state.rate <= 0 ? 1f : state.rate);
        state.receivedAt = System.currentTimeMillis();
    }

    private static void syncCurrentRoomFromList(JsonObject room) {
        if (GroupClient.roomId == null || !Objects.equals(GroupClient.roomId, string(room, "roomId"))) return;
        String roomName = stringValue(room, "name", "roomName");
        if (!roomName.isEmpty()) {
            GroupClient.roomName = roomName;
            if (GroupClient.state != null) GroupClient.state.roomName = roomName;
        }
        String hostUuid = string(room, "hostUuid");
        if (!hostUuid.isEmpty()) {
            GroupClient.hostUuid = hostUuid;
            if (GroupClient.state != null) GroupClient.state.hostUuid = hostUuid;
        }
        String hostName = string(room, "hostName");
        if (!hostName.isEmpty()) {
            GroupClient.hostName = hostName;
            if (GroupClient.state != null) GroupClient.state.hostName = hostName;
        }
        GroupClient.syncMemberCount(intValue(room, "members", -1));
    }

    private static JsonObject payload(JsonObject object) {
        return object.has("payload") && object.get("payload").isJsonObject() ? object.getAsJsonObject("payload") : object;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsFloat() : fallback;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String stringValue(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = string(object, key);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static void message(String text, Formatting formatting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(formatting), false);
        }
    }
}
