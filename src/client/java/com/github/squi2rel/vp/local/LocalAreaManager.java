package com.github.squi2rel.vp.local;

import com.github.squi2rel.vp.VideoPlayerClient;
import com.github.squi2rel.vp.group.GroupClient;
import com.github.squi2rel.vp.video.ClientVideoArea;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class LocalAreaManager {
    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("videoplayer-local-areas.json");
    private static final Gson gson = new Gson();
    private static final HashMap<String, ClientVideoArea> loadedAreas = new HashMap<>();
    private static LocalAreaConfig config = new LocalAreaConfig();

    public static void init() {
        load();
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            unloadAll();
            return;
        }

        String server = getServerKey(client);
        String dimension = client.world.getRegistryKey().getValue().toString();
        Vec3d pos = client.player.getPos();

        for (LocalAreaEntry entry : config.areas) {
            String runtimeName = runtimeName(entry);
            boolean shouldLoad = Objects.equals(server, entry.server)
                    && Objects.equals(dimension, entry.dimension)
                    && inBounds(pos, entry);
            boolean loaded = loadedAreas.containsKey(runtimeName);
            if (shouldLoad && !loaded) {
                loadArea(entry, runtimeName);
            } else if (!shouldLoad && loaded) {
                unloadArea(runtimeName);
            }
        }

        Iterator<Map.Entry<String, ClientVideoArea>> iterator = loadedAreas.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ClientVideoArea> loaded = iterator.next();
            if (getAreaByRuntimeName(loaded.getKey()) == null) {
                VideoPlayerClient.areas.remove(loaded.getKey());
                loaded.getValue().remove();
                iterator.remove();
            }
        }
    }

    public static void load() {
        try {
            config = gson.fromJson(Files.readString(configPath), LocalAreaConfig.class);
            if (config == null) config = new LocalAreaConfig();
            if (config.areas == null) config.areas = new ArrayList<>();
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

    public static String getCurrentServerKey() {
        return getServerKey(MinecraftClient.getInstance());
    }

    public static String getRuntimeName(String name) {
        return "local:" + name;
    }

    public static void reloadArea(String name) {
        unloadArea(getRuntimeName(name));
        tick();
    }

    public static void addArea(LocalAreaEntry area) {
        if (area == null) return;
        removeArea(area.name);
        config.areas.add(area);
        save();
    }

    public static void removeArea(String name) {
        String runtimeName = getRuntimeName(name);
        unloadArea(runtimeName);
        config.areas.removeIf(area -> Objects.equals(area.name, name));
        save();
    }

    public static LocalAreaEntry getArea(String name) {
        for (LocalAreaEntry area : config.areas) {
            if (Objects.equals(area.name, name)) return area;
        }
        return null;
    }

    private static void loadArea(LocalAreaEntry entry, String runtimeName) {
        ClientVideoArea area = new ClientVideoArea(entry.min, entry.max, runtimeName, entry.dimension);
        for (LocalScreenEntry screenEntry : entry.screens) {
            ClientVideoScreen screen = new ClientVideoScreen(area, screenEntry.name, screenEntry.p1, screenEntry.p2, screenEntry.p3, screenEntry.p4, screenEntry.source);
            screen.u1 = screenEntry.u1;
            screen.v1 = screenEntry.v1;
            screen.u2 = screenEntry.u2;
            screen.v2 = screenEntry.v2;
            screen.fill = screenEntry.fill;
            screen.scaleX = screenEntry.scaleX;
            screen.scaleY = screenEntry.scaleY;
            screen.meta = new HashMap<>(screenEntry.meta);
            area.addScreen(screen);
        }
        VideoPlayerClient.areas.put(runtimeName, area);
        loadedAreas.put(runtimeName, area);
        area.load();
        if (GroupClient.suspended && Objects.equals(GroupClient.boundArea, runtimeName)) {
            GroupClient.tryResumeFromRoomState();
        }
    }

    private static void unloadArea(String runtimeName) {
        ClientVideoArea area = loadedAreas.remove(runtimeName);
        if (area == null) return;
        if (Objects.equals(GroupClient.boundArea, runtimeName)) {
            GroupClient.suspendBecauseAreaUnloaded();
        }
        VideoPlayerClient.areas.remove(runtimeName);
        area.remove();
    }

    private static void unloadAll() {
        for (String name : new ArrayList<>(loadedAreas.keySet())) {
            unloadArea(name);
        }
    }

    private static boolean inBounds(Vec3d pos, LocalAreaEntry entry) {
        return entry.min.x <= pos.x && entry.min.y <= pos.y && entry.min.z <= pos.z
                && entry.max.x >= pos.x && entry.max.y >= pos.y && entry.max.z >= pos.z;
    }

    private static String getServerKey(MinecraftClient client) {
        if (client.isInSingleplayer()) return "singleplayer";
        ServerInfo server = client.getCurrentServerEntry();
        return server == null ? null : server.address;
    }

    private static String runtimeName(LocalAreaEntry entry) {
        return "local:" + entry.name;
    }

    private static LocalAreaEntry getAreaByRuntimeName(String runtimeName) {
        for (LocalAreaEntry area : config.areas) {
            if (Objects.equals(runtimeName(area), runtimeName)) return area;
        }
        return null;
    }
}
