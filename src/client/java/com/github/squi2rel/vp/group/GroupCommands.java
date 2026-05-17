package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerMain;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;

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
