package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class ScreenDescriptor {
    public String areaName;
    public String screenName;
    public String server;
    public String dimension;
    public Vector3f areaMin = new Vector3f();
    public Vector3f areaMax = new Vector3f();
    public Vector3f p1 = new Vector3f();
    public Vector3f p2 = new Vector3f();
    public Vector3f p3 = new Vector3f();
    public Vector3f p4 = new Vector3f();
    public float u1 = 0;
    public float v1 = 0;
    public float u2 = 1;
    public float v2 = 1;
    public boolean fill = false;
    public float scaleX = 1;
    public float scaleY = 1;
    public String source = "";
    public Map<String, Integer> meta = new HashMap<>();

    public static ScreenDescriptor from(String server, ClientVideoArea area, ClientVideoScreen screen) {
        ScreenDescriptor descriptor = new ScreenDescriptor();
        descriptor.areaName = area.name;
        descriptor.screenName = screen.name;
        descriptor.server = server;
        descriptor.dimension = area.dim;
        descriptor.areaMin = new Vector3f(area.min);
        descriptor.areaMax = new Vector3f(area.max);
        descriptor.p1 = new Vector3f(screen.p1);
        descriptor.p2 = new Vector3f(screen.p2);
        descriptor.p3 = new Vector3f(screen.p3);
        descriptor.p4 = new Vector3f(screen.p4);
        descriptor.u1 = screen.u1;
        descriptor.v1 = screen.v1;
        descriptor.u2 = screen.u2;
        descriptor.v2 = screen.v2;
        descriptor.fill = screen.fill;
        descriptor.scaleX = screen.scaleX;
        descriptor.scaleY = screen.scaleY;
        descriptor.source = screen.source == null ? "" : screen.source;
        descriptor.meta = screen.meta == null ? new HashMap<>() : new HashMap<>(screen.meta);
        return descriptor;
    }

    public String runtimeAreaName(String roomId) {
        return "group:" + roomId + ":" + areaName;
    }
}
