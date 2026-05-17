package com.github.squi2rel.vp.local;

import org.joml.Vector3f;

import java.util.ArrayList;

public class LocalAreaEntry {
    public String server;
    public String dimension;
    public String name;
    public Vector3f min = new Vector3f();
    public Vector3f max = new Vector3f();
    public ArrayList<LocalScreenEntry> screens = new ArrayList<>();
}
