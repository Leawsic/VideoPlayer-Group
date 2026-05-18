package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.provider.VideoInfo;

import java.util.ArrayList;

public class GroupRoomState {
    public String roomId;
    public String roomName;
    public String hostUuid;
    public String hostName;
    public ArrayList<GroupMember> members = new ArrayList<>();
    public ArrayList<GroupPlaylistItem> playlist = new ArrayList<>();
    public VideoInfo currentVideo;
    public long currentProgress;
    public long progress;
    public long baseProgress;
    public long baseServerTime;
    public long serverTime;
    public boolean paused;
    public float rate = 1f;
    public long receivedAt;
    public ScreenDescriptor hostScreen;
    public long seq;

    public long estimateProgress() {
        if (currentVideo == null) return 0;
        long anchor = progress > 0 ? progress : currentProgress;
        if (paused) return Math.max(anchor, 0);
        long elapsed = receivedAt > 0 ? Math.max(System.currentTimeMillis() - receivedAt, 0) : 0;
        float playbackRate = rate <= 0 ? 1f : rate;
        return Math.max(anchor + Math.round(elapsed * playbackRate), 0);
    }
}
