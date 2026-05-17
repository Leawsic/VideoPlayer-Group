package com.github.squi2rel.vp.group;

import java.util.ArrayList;

public class GroupRoomState {
    public String roomId;
    public String roomName;
    public String hostUuid;
    public ArrayList<GroupMember> members = new ArrayList<>();
    public ArrayList<GroupPlaylistItem> playlist = new ArrayList<>();
    public long seq;
}
