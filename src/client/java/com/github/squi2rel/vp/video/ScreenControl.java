package com.github.squi2rel.vp.video;

import com.github.squi2rel.vp.provider.VideoInfo;

public final class ScreenControl {
    private ScreenControl() {
    }

    public static void play(ClientVideoScreen screen, VideoInfo info, long progress) {
        if (screen == null || info == null) return;
        if (progress > 0) screen.setToSeek(progress);
        screen.play(info);
    }

    public static void stop(ClientVideoScreen screen) {
        if (screen == null) return;
        screen.stopPlayback();
    }

    public static void pause(ClientVideoScreen screen, boolean paused, long progress) {
        if (screen == null || screen.player == null) return;
        screen.player.pause(paused);
        if (progress >= 0) screen.setProgress(progress);
    }

    public static void seek(ClientVideoScreen screen, long progress) {
        if (screen == null || screen.player == null) return;
        screen.setProgress(progress);
    }

    public static void updatePlaylist(ClientVideoScreen screen, VideoInfo[] infos) {
        if (screen == null || infos == null) return;
        screen.updatePlaylist(infos);
    }
}
