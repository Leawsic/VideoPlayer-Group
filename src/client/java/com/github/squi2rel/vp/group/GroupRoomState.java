package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.provider.VideoInfo;

import java.util.ArrayList;

public class GroupRoomState {
    public String roomId;
    public String roomName;
    public String hostUuid;
    public ArrayList<GroupMember> members = new ArrayList<>();
    public ArrayList<GroupPlaylistItem> playlist = new ArrayList<>();
    public VideoInfo currentVideo;
    public long currentProgress;
    public boolean paused;
    public ScreenDescriptor hostScreen;
    public long seq;
}
