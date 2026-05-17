package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.ScreenControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;

public class GroupClient {
    public static final GroupConnection connection = new GroupConnection();

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
        ScreenControl.stop(screen);
        suspended = true;
        suspendReason = "area_unloaded";
        message("群组播放已因离开区域暂停", Formatting.YELLOW);
    }

    public static void tryResumeFromRoomState() {
        if (!suspended || roomId == null || state == null || state.currentVideo == null) return;
        ClientVideoScreen screen = getBoundScreen();
        if (screen == null) return;
        long progress = latestSync == null ? state.currentProgress : estimateProgress(latestSync);
        ScreenControl.play(screen, state.currentVideo, progress);
        if (state.paused || latestSync != null && latestSync.paused) {
            ScreenControl.pause(screen, true, progress);
        }
        suspended = false;
        suspendReason = null;
        message("群组播放已恢复", Formatting.GREEN);
    }

    private static long estimateProgress(VideoInfoSync sync) {
        if (sync.paused) return sync.progress;
        return sync.progress + Math.max(System.currentTimeMillis() - sync.clientTime, 0);
    }

    private static void message(String text, Formatting formatting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(formatting), false);
        }
    }

    public static class VideoInfoSync {
        public long progress;
        public boolean paused;
        public float rate;
        public long clientTime;
    }
}
