package com.github.squi2rel.vp.local;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class LocalAreaManager {
    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("videoplayer-local-areas.json");
    private static final Gson gson = new Gson();
    private static LocalAreaConfig config = new LocalAreaConfig();

    public static void init() {
        load();
    }

    public static void load() {
        try {
            config = gson.fromJson(Files.readString(configPath), LocalAreaConfig.class);
            if (config == null) config = new LocalAreaConfig();
            if (config.areas == null) config.areas = new java.util.ArrayList<>();
        } catch (Exception e) {
            config = new LocalAreaConfig();
            save();
        }
    }

    public static void save() {
        try {
            Files.writeString(configPath, gson.toJson(config));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static LocalAreaConfig getConfig() {
        return config;
    }

    public static void addArea(LocalAreaEntry area) {
        if (area == null) return;
        removeArea(area.name);
        config.areas.add(area);
        save();
    }

    public static void removeArea(String name) {
        config.areas.removeIf(area -> Objects.equals(area.name, name));
        save();
    }

    public static LocalAreaEntry getArea(String name) {
        for (LocalAreaEntry area : config.areas) {
            if (Objects.equals(area.name, name)) return area;
        }
        return null;
    }
}
