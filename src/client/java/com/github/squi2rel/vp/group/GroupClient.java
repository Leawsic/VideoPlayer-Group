package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.local.LocalAreaManager;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.IVideoPlayer;
import com.github.squi2rel.vp.video.ScreenControl;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GroupClient {
    private static final Gson gson = new Gson();
    public static final GroupConnection connection = new GroupConnection();
    private static long seq;
    private static int nextBindingOptionId;
    private static String hostRuntimeArea;

    public static String roomId;
    public static String roomName;
    public static String hostUuid;
    public static String hostName;
    public static boolean host;
    public static ArrayList<GroupMember> members = new ArrayList<>();
    public static int memberCount;
    public static GroupRoomState state;
    public static long lastSeq;
    public static String boundArea;
    public static String boundScreen;
    public static VideoInfoSync latestSync;
    public static boolean suspended;
    public static String suspendReason;
    public static ScreenDescriptor hostScreen;
    public static boolean usingHostScreen;
    public static Map<Integer, BindingOption> pendingBindingOptions = new HashMap<>();

    public static void updateState(GroupRoomState roomState, String playerUuid) {
        state = roomState;
        roomId = roomState.roomId;
        roomName = roomState.roomName;
        hostUuid = roomState.hostUuid;
        hostName = roomState.hostName;
        members = roomState.members == null ? new ArrayList<>() : roomState.members;
        memberCount = members.size();
        hostScreen = roomState.hostScreen;
        lastSeq = roomState.seq;
        host = hostUuid != null && hostUuid.equals(playerUuid);
    }

    public static JsonObject packet(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("seq", ++seq);
        json.add("payload", new JsonObject());
        return json;
    }

    public static JsonObject payload(JsonObject packet) {
        return packet.getAsJsonObject("payload");
    }

    public static void send(JsonObject packet) {
        connection.send(gson.toJson(packet));
    }

    public static void resetSeq() {
        seq = 0;
    }

    public static void clearRoom() {
        stopBoundPlayback();
        cleanupHostScreen();
        roomId = null;
        roomName = null;
        hostUuid = null;
        hostName = null;
        host = false;
        members = new ArrayList<>();
        memberCount = 0;
        state = null;
        lastSeq = 0;
        latestSync = null;
        suspended = false;
        suspendReason = null;
        hostScreen = null;
        usingHostScreen = false;
        pendingBindingOptions.clear();
        LocalAreaManager.tick();
    }

    public static void stopBoundPlayback() {
        ScreenControl.stop(getBoundScreen());
    }

    public static void bind(String area, String screen) {
        boundArea = area;
        boundScreen = screen;
        usingHostScreen = area != null && area.startsWith("group:");
    }

    public static void unbind() {
        boundArea = null;
        boundScreen = null;
        usingHostScreen = false;
    }

    public static ClientVideoScreen getBoundScreen() {
        if (!isBound()) return null;
        ClientVideoArea area = VideoPlayerClient.areas.get(boundArea);
        if (area == null || !area.loaded) return null;
        return area.getScreen(boundScreen);
    }

    public static boolean isBound() {
        return boundArea != null && boundScreen != null;
    }

    public static boolean isInRoom() {
        return roomId != null;
    }

    public static boolean isBoundToArea(String runtimeAreaName) {
        return Objects.equals(boundArea, runtimeAreaName);
    }

    public static boolean shouldKeepLocalAreaLoaded(String runtimeAreaName) {
        return isInRoom() && isBoundToArea(runtimeAreaName) && runtimeAreaName.startsWith("local:");
    }

    public static void upsertMember(String uuid, String name) {
        if ((uuid == null || uuid.isEmpty()) && (name == null || name.isEmpty())) return;
        GroupMember member = findMember(uuid, name);
        if (member == null) {
            member = new GroupMember();
            members.add(member);
        }
        if (uuid != null && !uuid.isEmpty()) member.uuid = uuid;
        if (name != null && !name.isEmpty()) member.name = name;
        member.host = hostUuid != null && hostUuid.equals(member.uuid);
        memberCount = Math.max(memberCount, members.size());
        if (state != null) state.members = members;
    }

    public static void removeMember(String uuid, String name) {
        boolean removed = members.removeIf(member -> matchesMember(member, uuid, name));
        if (removed) memberCount = members.size();
        if (state != null) state.members = members;
    }

    public static void syncMemberCount(int count) {
        if (count >= 0) memberCount = count;
    }

    public static int getMemberCount() {
        return Math.max(memberCount, members.size());
    }

    public static String getHostDisplayName() {
        return hostName == null || hostName.isEmpty() ? hostUuid : hostName;
    }

    public static void sendBoundScreenToServer() {
        if (!isInRoom() || !host || !connection.isConnected()) return;
        ClientVideoArea area = VideoPlayerClient.areas.get(boundArea);
        ClientVideoScreen screen = getBoundScreen();
        if (area == null || screen == null) return;
        ScreenDescriptor descriptor = ScreenDescriptor.from(LocalAreaManager.getCurrentServerKey(), area, screen);
        hostScreen = descriptor;
        if (state != null) state.hostScreen = descriptor;
        JsonObject json = packet("screen_bind");
        payload(json).add("screen", gson.toJsonTree(descriptor));
        send(json);
    }

    public static void sendScreenUnbindToServer() {
        if (!isInRoom() || !host || !connection.isConnected()) return;
        JsonObject json = packet("screen_unbind");
        if (hostScreen != null) {
            payload(json).addProperty("areaName", hostScreen.areaName);
            payload(json).addProperty("screenName", hostScreen.screenName);
        }
        send(json);
        hostScreen = null;
        if (state != null) state.hostScreen = null;
    }

    public static void onHostScreenReceived(ScreenDescriptor screen) {
        hostScreen = screen;
        if (state != null) state.hostScreen = screen;
        if (screen == null || host) return;
        ArrayList<BindingOption> localOptions = collectAvailableScreens();
        if (localOptions.isEmpty()) {
            bindHostScreen();
            message("未发现本地可用屏幕，已自动使用群主广播屏幕。", Formatting.GREEN);
            return;
        }
        showBindingChoices(localOptions, screen);
    }

    public static void onHostScreenUnbound() {
        hostScreen = null;
        if (state != null) state.hostScreen = null;
        pendingBindingOptions.clear();
        if (usingHostScreen) {
            stopBoundPlayback();
            unbind();
        }
        removeHostRuntimeArea();
        message("群主已取消共享屏幕", Formatting.YELLOW);
    }

    public static boolean bindHostScreen() {
        if (hostScreen == null || roomId == null) return false;
        createRuntimeAreaFromHostScreen(hostScreen);
        restoreCurrentVideoToBoundScreen();
        return true;
    }

    public static void createRuntimeAreaFromHostScreen(ScreenDescriptor screen) {
        removeHostRuntimeArea();
        String runtimeAreaName = screen.runtimeAreaName(roomId);
        ClientVideoArea area = new ClientVideoArea(screen.areaMin, screen.areaMax, runtimeAreaName, screen.dimension);
        ClientVideoScreen clientScreen = new ClientVideoScreen(area, screen.screenName, screen.p1, screen.p2, screen.p3, screen.p4, screen.source == null ? "" : screen.source);
        clientScreen.u1 = screen.u1;
        clientScreen.v1 = screen.v1;
        clientScreen.u2 = screen.u2;
        clientScreen.v2 = screen.v2;
        clientScreen.fill = screen.fill;
        clientScreen.scaleX = screen.scaleX;
        clientScreen.scaleY = screen.scaleY;
        clientScreen.meta = screen.meta == null ? new HashMap<>() : new HashMap<>(screen.meta);
        area.addScreen(clientScreen);
        VideoPlayerClient.areas.put(runtimeAreaName, area);
        area.load();
        hostRuntimeArea = runtimeAreaName;
        boundArea = runtimeAreaName;
        boundScreen = screen.screenName;
        usingHostScreen = true;
    }

    public static ArrayList<BindingOption> collectAvailableScreens() {
        ArrayList<BindingOption> options = new ArrayList<>();
        for (ClientVideoArea area : new ArrayList<>(VideoPlayerClient.areas.values())) {
            if (area == null || !area.loaded || area.name == null || area.name.startsWith("group:")) continue;
            for (var rawScreen : area.screens) {
                if (!(rawScreen instanceof ClientVideoScreen screen)) continue;
                if (screen.source != null && !screen.source.isEmpty()) continue;
                BindingOption option = new BindingOption();
                option.area = area.name;
                option.screen = screen.name;
                option.label = "绑定本地屏幕 " + area.name + "/" + screen.name;
                option.hostScreen = false;
                options.add(option);
            }
        }
        return options;
    }

    public static void showBindingChoices(ArrayList<BindingOption> localOptions, ScreenDescriptor descriptor) {
        pendingBindingOptions.clear();
        nextBindingOptionId = 1;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.sendMessage(Text.literal("群主已共享屏幕，请选择用于群组播放的屏幕：").formatted(Formatting.GOLD), false);
        for (BindingOption option : localOptions) {
            addBindingOption(option);
            sendBindingOptionMessage(option);
        }
        BindingOption hostOption = new BindingOption();
        hostOption.label = "使用群主广播屏幕 " + descriptor.areaName + "/" + descriptor.screenName;
        hostOption.descriptor = descriptor;
        hostOption.hostScreen = true;
        addBindingOption(hostOption);
        sendBindingOptionMessage(hostOption);
    }

    public static boolean bindOption(int optionId) {
        BindingOption option = pendingBindingOptions.get(optionId);
        if (option == null) return false;
        if (option.hostScreen) {
            hostScreen = option.descriptor;
            bindHostScreen();
        } else {
            bind(option.area, option.screen);
            restoreCurrentVideoToBoundScreen();
        }
        return true;
    }

    public static void restoreCurrentVideoToBoundScreen() {
        if (state == null || state.currentVideo == null) return;
        ClientVideoScreen screen = getBoundScreen();
        if (screen == null) return;
        long progress = host || latestSync == null ? state.currentProgress : estimateProgress(latestSync);
        showCurrentVideo(screen, state.currentVideo);
        ScreenControl.play(screen, state.currentVideo, progress);
        if (state.paused || latestSync != null && latestSync.paused) {
            ScreenControl.pause(screen, true, progress);
        }
    }

    public static void removeHostRuntimeArea() {
        if (hostRuntimeArea == null) return;
        ClientVideoArea area = VideoPlayerClient.areas.remove(hostRuntimeArea);
        if (area != null) area.remove();
        if (Objects.equals(boundArea, hostRuntimeArea)) unbind();
        hostRuntimeArea = null;
        usingHostScreen = false;
    }

    public static void suspendBecauseAreaUnloaded() {
        ClientVideoScreen screen = getBoundScreen();
        savePlaybackState(screen);
        ScreenControl.stop(screen);
        suspended = true;
        suspendReason = "area_unloaded";
        message("群组播放已因离开区域暂停", Formatting.YELLOW);
    }

    public static void tryResumeFromRoomState() {
        if (!suspended || roomId == null || state == null || state.currentVideo == null) return;
        restoreCurrentVideoToBoundScreen();
        suspended = false;
        suspendReason = null;
        message("群组播放已恢复", Formatting.GREEN);
    }

    public static void showCurrentVideo(ClientVideoScreen screen, VideoInfo info) {
        ScreenControl.updatePlaylist(screen, info == null ? new VideoInfo[0] : new VideoInfo[]{info});
    }

    public static void savePlaybackState(ClientVideoScreen screen) {
        if (screen == null || screen.player == null) return;
        savePlaybackState(screen.player);
    }

    public static void savePlaybackState(IVideoPlayer player) {
        if (state == null || player == null) return;
        long progress = player.getProgress();
        if (progress >= 0) state.currentProgress = progress;
        latestSync = new VideoInfoSync();
        latestSync.progress = Math.max(state.currentProgress, 0);
        latestSync.paused = player.isPaused();
        latestSync.rate = 1f;
        latestSync.clientTime = System.currentTimeMillis();
    }

    private static void cleanupHostScreen() {
        pendingBindingOptions.clear();
        removeHostRuntimeArea();
        hostScreen = null;
        usingHostScreen = false;
    }

    private static GroupMember findMember(String uuid, String name) {
        for (GroupMember member : members) {
            if (matchesMember(member, uuid, name)) return member;
        }
        return null;
    }

    private static boolean matchesMember(GroupMember member, String uuid, String name) {
        if (member == null) return false;
        if (uuid != null && !uuid.isEmpty() && Objects.equals(member.uuid, uuid)) return true;
        return name != null && !name.isEmpty() && Objects.equals(member.name, name);
    }

    private static void addBindingOption(BindingOption option) {
        option.id = nextBindingOptionId++;
        pendingBindingOptions.put(option.id, option);
    }

    private static void sendBindingOptionMessage(BindingOption option) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        MutableText text = Text.literal("[" + option.label + "]")
                .formatted(option.hostScreen ? Formatting.AQUA : Formatting.GREEN)
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vlc group bind-option " + option.id)));
        client.player.sendMessage(text, false);
    }

    private static long estimateProgress(VideoInfoSync sync) {
        if (sync.paused) return sync.progress;
        return sync.progress + Math.max(System.currentTimeMillis() - sync.clientTime, 0);
    }

    private static void message(String text, Formatting formatting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(formatting), true);
        }
    }

    public static class BindingOption {
        public int id;
        public String label;
        public String area;
        public String screen;
        public ScreenDescriptor descriptor;
        public boolean hostScreen;
    }

    public static class VideoInfoSync {
        public long progress;
        public boolean paused;
        public float rate;
        public long clientTime;
    }
}
