package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerMain;
import com.github.squi2rel.vp.provider.NamedProviderSource;
import com.github.squi2rel.vp.provider.VideoInfo;
import com.github.squi2rel.vp.provider.VideoProviders;
import com.github.squi2rel.vp.video.ClientVideoScreen;
import com.github.squi2rel.vp.video.ScreenControl;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

public class GroupCommands {
    private static final Gson gson = new Gson();

    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return ClientCommandManager.literal("group")
                .then(ClientCommandManager.literal("connect")
                        .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                .executes(s -> connect(s.getSource(), s.getArgument("url", String.class)))))
                .then(ClientCommandManager.literal("disconnect")
                        .executes(s -> disconnect(s.getSource())))
                .then(ClientCommandManager.literal("raw")
                        .then(ClientCommandManager.argument("json", StringArgumentType.greedyString())
                                .executes(s -> raw(s.getSource(), s.getArgument("json", String.class)))))
                .then(ClientCommandManager.literal("create")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(s -> create(s.getSource(), s.getArgument("name", String.class)))))
                .then(ClientCommandManager.literal("list")
                        .executes(s -> list(s.getSource())))
                .then(ClientCommandManager.literal("join")
                        .then(ClientCommandManager.argument("roomId", StringArgumentType.string())
                                .executes(s -> join(s.getSource(), s.getArgument("roomId", String.class)))))
                .then(ClientCommandManager.literal("leave")
                        .executes(s -> leave(s.getSource())))
                .then(ClientCommandManager.literal("disband")
                        .executes(s -> disband(s.getSource())))
                .then(ClientCommandManager.literal("bind")
                        .then(ClientCommandManager.argument("area", StringArgumentType.string())
                                .then(ClientCommandManager.argument("screen", StringArgumentType.string())
                                        .executes(s -> bind(s.getSource(), s.getArgument("area", String.class), s.getArgument("screen", String.class))))))
                .then(ClientCommandManager.literal("unbind")
                        .executes(s -> unbind(s.getSource())))
                .then(ClientCommandManager.literal("play")
                        .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                .executes(s -> play(s.getSource(), s.getArgument("url", String.class)))))
                .then(ClientCommandManager.literal("stop")
                        .executes(s -> stop(s.getSource())))
                .then(ClientCommandManager.literal("pause")
                        .executes(s -> pause(s.getSource())))
                .then(ClientCommandManager.literal("resume")
                        .executes(s -> resume(s.getSource())))
                .then(ClientCommandManager.literal("seek")
                        .then(ClientCommandManager.argument("seconds", FloatArgumentType.floatArg(0))
                                .executes(s -> seek(s.getSource(), s.getArgument("seconds", Float.class)))))
                .then(ClientCommandManager.literal("status")
                        .executes(s -> status(s.getSource())));
    }

    private static int connect(FabricClientCommandSource source, String url) {
        try {
            GroupClient.connection.connect(URI.create(url), createHello());
            source.sendFeedback(Text.literal("正在连接房间服务器...").formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(Text.literal("连接地址无效: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int disconnect(FabricClientCommandSource source) {
        GroupClient.connection.disconnect();
        GroupClient.clearRoom();
        source.sendFeedback(Text.literal("已请求断开房间服务器连接").formatted(Formatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int raw(FabricClientCommandSource source, String json) {
        if (!checkConnected(source)) return 0;
        GroupClient.connection.send(json);
        source.sendFeedback(Text.literal("已发送原始 JSON").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int create(FabricClientCommandSource source, String name) {
        if (!checkConnected(source)) return 0;
        JsonObject json = packet("room_create");
        json.addProperty("name", name);
        send(json);
        source.sendFeedback(Text.literal("已发送创建房间请求").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        if (!checkConnected(source)) return 0;
        send(packet("room_list"));
        source.sendFeedback(Text.literal("已发送房间列表请求").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int join(FabricClientCommandSource source, String roomId) {
        if (!checkConnected(source)) return 0;
        JsonObject json = packet("room_join");
        json.addProperty("roomId", roomId);
        send(json);
        source.sendFeedback(Text.literal("已发送加入房间请求").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int leave(FabricClientCommandSource source) {
        if (!checkConnected(source)) return 0;
        send(packet("room_leave"));
        GroupClient.clearRoom();
        source.sendFeedback(Text.literal("已发送离开房间请求").formatted(Formatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int disband(FabricClientCommandSource source) {
        if (!checkConnected(source)) return 0;
        send(packet("room_disband"));
        source.sendFeedback(Text.literal("已发送解散房间请求").formatted(Formatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int bind(FabricClientCommandSource source, String areaName, String screenName) {
        GroupClient.bind(areaName, screenName);
        if (GroupClient.getBoundScreen() == null) {
            GroupClient.unbind();
            source.sendFeedback(Text.literal("绑定失败：区域或屏幕不存在，或区域尚未加载").formatted(Formatting.RED));
            return 0;
        }
        source.sendFeedback(Text.literal("已绑定群组屏幕: " + areaName + " / " + screenName).formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int unbind(FabricClientCommandSource source) {
        GroupClient.unbind();
        source.sendFeedback(Text.literal("已取消群组屏幕绑定").formatted(Formatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int play(FabricClientCommandSource source, String url) {
        if (!checkPlaybackReady(source)) return 0;
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.player == null ? "" : client.player.getName().getString();
        CompletableFuture<VideoInfo> future = VideoProviders.from(url, new NamedProviderSource(playerName));
        if (future == null) {
            source.sendFeedback(Text.literal("无法解析视频源").formatted(Formatting.RED));
            return 0;
        }
        source.sendFeedback(Text.literal("正在解析群组视频...").formatted(Formatting.YELLOW));
        CompletableFuture.supplyAsync(() -> {
            try {
                return future.get();
            } catch (Exception e) {
                LOGGER.error(e.toString());
                return null;
            }
        }).thenAccept(info -> MinecraftClient.getInstance().execute(() -> {
            if (info == null) {
                source.sendFeedback(Text.literal("无法解析视频源").formatted(Formatting.RED));
                return;
            }
            ClientVideoScreen screen = GroupClient.getBoundScreen();
            if (screen == null) {
                source.sendFeedback(Text.literal("群组屏幕未绑定或当前不可用").formatted(Formatting.RED));
                return;
            }
            ScreenControl.play(screen, info, 0);
            JsonObject json = packet("play");
            json.add("video", gson.toJsonTree(info));
            json.addProperty("progress", 0);
            send(json);
            source.sendFeedback(Text.literal("已发送群组播放").formatted(Formatting.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(FabricClientCommandSource source) {
        if (!checkPlaybackReady(source)) return 0;
        ScreenControl.stop(GroupClient.getBoundScreen());
        send(packet("stop"));
        source.sendFeedback(Text.literal("已发送群组停止").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int pause(FabricClientCommandSource source) {
        if (!checkPlaybackReady(source)) return 0;
        ScreenControl.pause(GroupClient.getBoundScreen(), true, -1);
        send(packet("pause"));
        source.sendFeedback(Text.literal("已发送群组暂停").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int resume(FabricClientCommandSource source) {
        if (!checkPlaybackReady(source)) return 0;
        ScreenControl.pause(GroupClient.getBoundScreen(), false, -1);
        JsonObject json = packet("pause");
        json.addProperty("paused", false);
        send(json);
        source.sendFeedback(Text.literal("已发送群组继续播放").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int seek(FabricClientCommandSource source, float seconds) {
        if (!checkPlaybackReady(source)) return 0;
        long progress = (long) (seconds * 1000);
        ScreenControl.seek(GroupClient.getBoundScreen(), progress);
        JsonObject json = packet("seek");
        json.addProperty("progress", progress);
        send(json);
        source.sendFeedback(Text.literal("已发送群组跳转到 " + seconds + " 秒").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(FabricClientCommandSource source) {
        String bound = GroupClient.isBound() ? GroupClient.boundArea + " / " + GroupClient.boundScreen : "未绑定";
        String boundState = GroupClient.isBound() && GroupClient.getBoundScreen() == null ? "（当前不可用）" : "";
        if (GroupClient.roomId == null) {
            source.sendFeedback(Text.literal("当前未加入房间\n绑定屏幕: " + bound + boundState).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(Text.literal("房间: %s (%s)\n房主: %s%s\n成员: %d\n序号: %d\n绑定屏幕: %s%s".formatted(
                GroupClient.roomName,
                GroupClient.roomId,
                GroupClient.hostUuid,
                GroupClient.host ? "（你是房主）" : "",
                GroupClient.members.size(),
                GroupClient.lastSeq,
                bound,
                boundState
        )).formatted(Formatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean checkPlaybackReady(FabricClientCommandSource source) {
        if (!checkConnected(source)) return false;
        if (GroupClient.roomId == null) {
            source.sendFeedback(Text.literal("尚未加入群组房间").formatted(Formatting.RED));
            return false;
        }
        if (GroupClient.getBoundScreen() != null) return true;
        source.sendFeedback(Text.literal("群组屏幕未绑定或当前不可用").formatted(Formatting.RED));
        return false;
    }

    private static boolean checkConnected(FabricClientCommandSource source) {
        if (GroupClient.connection.isConnected()) return true;
        source.sendFeedback(Text.literal("尚未连接房间服务器").formatted(Formatting.RED));
        return false;
    }

    private static void send(JsonObject json) {
        GroupClient.connection.send(gson.toJson(json));
    }

    private static JsonObject packet(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    private static String createHello() {
        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("protocol", 1);
        json.addProperty("playerUuid", client.player == null ? "" : client.player.getUuidAsString());
        json.addProperty("playerName", client.player == null ? "" : client.player.getName().getString());
        json.addProperty("minecraftServer", getServerKey(client));
        json.addProperty("modVersion", VideoPlayerMain.version);
        return gson.toJson(json);
    }

    private static String getServerKey(MinecraftClient client) {
        if (client.isInSingleplayer()) return "singleplayer";
        ServerInfo server = client.getCurrentServerEntry();
        return server == null ? "" : server.address;
    }
}
