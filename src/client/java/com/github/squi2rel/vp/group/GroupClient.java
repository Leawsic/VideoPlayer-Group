package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.IVideoPlayer;
import com.github.squi2rel.vp.video.ScreenControl;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;

public class GroupClient {
    private static final Gson gson = new Gson();
    public static final GroupConnection connection = new GroupConnection();
    private static long seq;

    public static String roomId;
    public static String roomName;
    public static String hostUuid;
    public static boolean host;
    public static ArrayList<GroupMember> members = new ArrayList<>();
    public static GroupRoomState state;
    public static long lastSeq;
    public static String boundArea;
    public static String boundScreen;
    public static VideoInfoSync latestSync;
    public static boolean suspended;
    public static String suspendReason;

    public static void updateState(GroupRoomState roomState, String playerUuid) {
        state = roomState;
        roomId = roomState.roomId;
        roomName = roomState.roomName;
        hostUuid = roomState.hostUuid;
        members = roomState.members == null ? new ArrayList<>() : roomState.members;
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
        roomId = null;
        roomName = null;
        hostUuid = null;
        host = false;
        members = new ArrayList<>();
        state = null;
        lastSeq = 0;
        latestSync = null;
        suspended = false;
        suspendReason = null;
    }

    public static void stopBoundPlayback() {
        ScreenControl.stop(getBoundScreen());
    }

    public static void bind(String area, String screen) {
        boundArea = area;
        boundScreen = screen;
    }

    public static void unbind() {
        boundArea = null;
        boundScreen = null;
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
        ClientVideoScreen screen = getBoundScreen();
        if (screen == null) return;
        long progress = host || latestSync == null ? state.currentProgress : estimateProgress(latestSync);
        showCurrentVideo(screen, state.currentVideo);
        ScreenControl.play(screen, state.currentVideo, progress);
        if (state.paused || latestSync != null && latestSync.paused) {
            ScreenControl.pause(screen, true, progress);
        }
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

    public static class VideoInfoSync {
        public long progress;
        public boolean paused;
        public float rate;
        public long clientTime;
    }
}
