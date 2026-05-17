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
                .map(room -> "%s - %s".formatted(string(room, "roomId"), stringValue(room, "name", "roomName")))
                .collect(Collectors.joining("\n"));
        message("房间列表:\n" + text, Formatting.GOLD);
    }

    private static void roomState(JsonObject object, JsonObject envelope) {
        GroupRoomState state = object.has("state") && object.get("state").isJsonObject()
                ? gson.fromJson(object.get("state"), GroupRoomState.class)
                : gson.fromJson(object, GroupRoomState.class);
        if (state.roomName == null || state.roomName.isEmpty()) state.roomName = stringValue(object, "name", "roomName");
        if (state.currentProgress == 0) state.currentProgress = longValue(object, "progress", 0);
        if (state.seq == 0) state.seq = longValue(envelope, "seq", 0);
        MinecraftClient client = MinecraftClient.getInstance();
        GroupClient.updateState(state, client.player == null ? "" : client.player.getUuidAsString());
        message("已更新房间状态: " + GroupClient.roomName + "，成员 " + GroupClient.members.size() + " 人", Formatting.GREEN);
    }

    private static void memberJoined(JsonObject object) {
        message("成员加入房间: " + string(object, "playerName"), Formatting.GREEN);
    }

    private static void memberLeft(JsonObject object) {
        message("成员离开房间: " + string(object, "playerName"), Formatting.YELLOW);
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
        }
        ClientVideoScreen screen = boundScreenOrWarn();
        if (screen == null) return;
        GroupClient.showCurrentVideo(screen, null);
        ScreenControl.stop(screen);
    }

    private static void pause(JsonObject object) {
        ClientVideoScreen screen = boundScreenOrWarn();
        if (screen == null) return;
        boolean paused = !object.has("paused") || object.get("paused").getAsBoolean();
        long progress = longValue(object, "progress", -1);
        ScreenControl.pause(screen, paused, progress);
    }

    private static void seek(JsonObject object) {
        long progress = longValue(object, "progress", 0);
        if (GroupClient.state != null) GroupClient.state.currentProgress = progress;
        ClientVideoScreen screen = boundScreenOrWarn();
        if (screen == null) return;
        ScreenControl.seek(screen, progress);
    }

    private static void syncState(JsonObject object) {
        GroupSyncManager.applySync(
                longValue(object, "progress", 0),
                object.has("paused") && object.get("paused").getAsBoolean(),
                object.has("rate") ? object.get("rate").getAsFloat() : 1f,
                longValue(object, "clientTime", System.currentTimeMillis())
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
        ClientVideoScreen screen = boundScreenOrWarn();
        if (screen == null) return;
        GroupClient.showCurrentVideo(screen, info);
        ScreenControl.play(screen, info, progress);
    }

    private static ClientVideoScreen boundScreenOrWarn() {
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen != null) return screen;
        message("已收到群组播放，但未绑定屏幕", Formatting.YELLOW);
        return null;
    }

    private static void error(JsonObject object) {
        JsonObject payload = object.has("payload") && object.get("payload").isJsonObject() ? object.getAsJsonObject("payload") : object;
        String code = string(payload, "code");
        String text = string(payload, "message");
        message("房间服务器错误: " + (code.isEmpty() ? "" : code + " ") + text, Formatting.RED);
    }

    private static JsonObject payload(JsonObject object) {
        return object.has("payload") && object.get("payload").isJsonObject() ? object.getAsJsonObject("payload") : object;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
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
