package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.IVideoPlayer;
import com.github.squi2rel.vp.video.ScreenControl;
import com.github.squi2rel.vp.video.VideoPlayer;
import com.google.gson.JsonObject;

public class GroupSyncManager {
    private static long lastSyncSend;
    private static long lastRestoreAttempt;

    public static void tick() {
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null) return;
        if (GroupClient.host) {
            sendHostSync(screen);
        } else {
            restorePlayback(screen);
        }
    }

    public static void applySync(long progress, boolean paused, float rate, long clientTime) {
        GroupClient.VideoInfoSync sync = new GroupClient.VideoInfoSync();
        sync.progress = progress;
        sync.paused = paused;
        sync.rate = rate;
        sync.clientTime = clientTime;
        GroupClient.latestSync = sync;

        if (GroupClient.host) return;
        ClientVideoScreen screen = GroupClient.getBoundScreen();
        if (screen == null) return;
        if (screen.player == null) {
            restorePlayback(screen);
            return;
        }
        correct(screen, estimatedHostProgress(sync));
    }

    private static void sendHostSync(ClientVideoScreen screen) {
        if (GroupClient.roomId == null || !GroupClient.connection.isConnected()) return;
        IVideoPlayer player = screen.player;
        if (player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSyncSend < 1000) return;
        lastSyncSend = now;

        JsonObject json = GroupClient.packet("sync_state");
        JsonObject payload = GroupClient.payload(json);
        payload.addProperty("progress", player.getProgress());
        payload.addProperty("paused", player.isPaused());
        payload.addProperty("rate", player instanceof VideoPlayer vp ? vp.getRate() : 1f);
        payload.addProperty("clientTime", now);
        GroupClient.send(json);
    }

    private static void restorePlayback(ClientVideoScreen screen) {
        if (screen.player != null) return;
        if (GroupClient.state == null || GroupClient.state.currentVideo == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRestoreAttempt < 1000) return;
        lastRestoreAttempt = now;
        ScreenControl.play(screen, GroupClient.state.currentVideo, currentProgress());
    }

    private static long currentProgress() {
        if (GroupClient.latestSync != null) return estimatedHostProgress(GroupClient.latestSync);
        return GroupClient.state == null ? 0 : GroupClient.state.currentProgress;
    }

    private static void correct(ClientVideoScreen screen, long hostProgress) {
        IVideoPlayer player = screen.player;
        if (player == null) return;
        long localProgress = player.getProgress();
        if (localProgress < 0 || hostProgress < 0) return;
        long delta = hostProgress - localProgress;
        long abs = Math.abs(delta);
        if (abs <= 50) {
            if (player instanceof VideoPlayer vp && vp.getRate() != 1f) vp.setRate(1f);
            return;
        }
        if (!(player instanceof VideoPlayer vp)) {
            ScreenControl.seek(screen, hostProgress);
            return;
        }
        if (abs <= 1000) {
            vp.setRate(delta > 0 ? 1.05f : 0.95f);
        } else if (abs <= 5000) {
            vp.setRate(delta > 0 ? 1.25f : 0.75f);
        } else {
            ScreenControl.seek(screen, hostProgress);
            vp.setRate(1f);
        }
    }

    private static long estimatedHostProgress(GroupClient.VideoInfoSync sync) {
        if (sync.paused) return sync.progress;
        long elapsed = System.currentTimeMillis() - sync.clientTime;
        return sync.progress + Math.max(elapsed, 0);
    }

    public static void setCurrentVideo(VideoInfo info, long progress) {
        if (GroupClient.state == null) GroupClient.state = new GroupRoomState();
        GroupClient.state.currentVideo = info;
        GroupClient.state.currentProgress = progress;
    }
}
