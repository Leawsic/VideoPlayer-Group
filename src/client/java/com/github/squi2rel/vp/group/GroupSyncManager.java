package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.IVideoPlayer;
import com.github.squi2rel.vp.video.ScreenControl;
import com.github.squi2rel.vp.video.VideoPlayer;

public class GroupSyncManager {
    private static long lastRestoreAttempt;

    public static void tick() {
        if (!GroupClient.isInRoom() || !GroupClient.isBound()) return;
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null || GroupClient.state == null || GroupClient.state.currentVideo == null) return;
        GroupClient.showCurrentVideo(screen, GroupClient.state.currentVideo);
        if (screen.player == null) {
            restorePlayback(screen);
            return;
        }
        correct(screen, GroupClient.state.estimateProgress());
    }

    public static void applySync(long progress, boolean paused, float rate, long serverTime) {
        GroupRoomState state = ensureState();
        state.progress = Math.max(progress, 0);
        state.currentProgress = state.progress;
        state.paused = paused;
        state.rate = rate <= 0 ? 1f : rate;
        state.serverTime = serverTime;
        state.receivedAt = System.currentTimeMillis();

        GroupClient.latestSync = new GroupClient.VideoInfoSync();
        GroupClient.latestSync.progress = state.progress;
        GroupClient.latestSync.paused = state.paused;
        GroupClient.latestSync.rate = state.rate;
        GroupClient.latestSync.clientTime = state.receivedAt;

        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null || state.currentVideo == null) return;
        GroupClient.showCurrentVideo(screen, state.currentVideo);
        if (screen.player == null) {
            restorePlayback(screen);
            return;
        }
        correct(screen, state.estimateProgress());
    }

    public static void applySync(long progress, long baseProgress, long baseServerTime, long serverTime, boolean paused, float rate) {
        GroupRoomState state = ensureState();
        state.baseProgress = Math.max(baseProgress, 0);
        state.baseServerTime = baseServerTime;
        applySync(progress, paused, rate, serverTime);
    }

    private static void restorePlayback(ClientVideoScreen screen) {
        if (screen.player != null) return;
        if (GroupClient.state == null || GroupClient.state.currentVideo == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRestoreAttempt < 1000) return;
        lastRestoreAttempt = now;
        long progress = GroupClient.state.estimateProgress();
        GroupClient.showCurrentVideo(screen, GroupClient.state.currentVideo);
        ScreenControl.play(screen, GroupClient.state.currentVideo, progress);
        if (GroupClient.state.paused) {
            ScreenControl.pause(screen, true, progress);
        }
    }

    private static void correct(ClientVideoScreen screen, long targetProgress) {
        IVideoPlayer player = screen.player;
        if (player == null) return;
        if (GroupClient.state != null && GroupClient.state.paused) {
            if (player instanceof VideoPlayer vp) vp.setRate(1f);
            ScreenControl.pause(screen, true, targetProgress);
            return;
        }
        if (player.isPaused()) {
            ScreenControl.pause(screen, false, targetProgress);
        }
        long localProgress = player.getProgress();
        if (localProgress < 0 || targetProgress < 0) return;
        long delta = targetProgress - localProgress;
        long abs = Math.abs(delta);
        if (abs <= 50) {
            if (player instanceof VideoPlayer vp && vp.getRate() != 1f) vp.setRate(1f);
            return;
        }
        if (!(player instanceof VideoPlayer vp)) {
            ScreenControl.seek(screen, targetProgress);
            return;
        }
        if (abs <= 1000) {
            vp.setRate(delta > 0 ? 1.05f : 0.95f);
        } else if (abs <= 5000) {
            vp.setRate(delta > 0 ? 1.3f : 0.8f);
        } else {
            ScreenControl.seek(screen, targetProgress);
            vp.setRate(1f);
        }
    }

    public static void setCurrentVideo(VideoInfo info, long progress) {
        GroupRoomState state = ensureState();
        long now = System.currentTimeMillis();
        state.currentVideo = info;
        state.progress = Math.max(progress, 0);
        state.currentProgress = state.progress;
        state.baseProgress = state.progress;
        state.baseServerTime = state.serverTime;
        state.rate = 1f;
        state.paused = false;
        state.receivedAt = now;
    }

    private static GroupRoomState ensureState() {
        if (GroupClient.state == null) GroupClient.state = new GroupRoomState();
        return GroupClient.state;
    }
}
