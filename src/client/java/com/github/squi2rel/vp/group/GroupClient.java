package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ClientVideoScreen;

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
        roomId = null;
        roomName = null;
        hostUuid = null;
        host = false;
        members = new ArrayList<>();
        state = null;
        lastSeq = 0;
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
}
