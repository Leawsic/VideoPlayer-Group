package com.github.squi2rel.vp.local;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class LocalScreenEntry {
    public String name;
    public Vector3f p1 = new Vector3f();
    public Vector3f p2 = new Vector3f();
    public Vector3f p3 = new Vector3f();
    public Vector3f p4 = new Vector3f();
    public float u1 = 0, v1 = 0, u2 = 1, v2 = 1;
    public boolean fill;
    public float scaleX = 1, scaleY = 1;
    public String source = "";
    public Map<String, Integer> meta = new HashMap<>();
}
