package com.github.squi2rel.vp.group;

import com.github.squi2rel.vp.VideoPlayerMain;
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
    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return ClientCommandManager.literal("group")
                .then(ClientCommandManager.literal("connect")
                        .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                .executes(s -> connect(s.getSource(), s.getArgument("url", String.class)))))
                .then(ClientCommandManager.literal("disconnect")
                        .executes(s -> disconnect(s.getSource())))
                .then(ClientCommandManager.literal("raw")
                        .then(ClientCommandManager.argument("json", StringArgumentType.greedyString())
                                .executes(s -> raw(s.getSource(), s.getArgument("json", String.class)))));
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
        source.sendFeedback(Text.literal("已请求断开房间服务器连接").formatted(Formatting.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static int raw(FabricClientCommandSource source, String json) {
        if (!GroupClient.connection.isConnected()) {
            source.sendFeedback(Text.literal("尚未连接房间服务器").formatted(Formatting.RED));
            return 0;
        }
        GroupClient.connection.send(json);
        source.sendFeedback(Text.literal("已发送原始 JSON").formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static String createHello() {
        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("protocol", 1);
        json.addProperty("playerUuid", client.player == null ? "" : client.player.getUuidAsString());
        json.addProperty("playerName", client.player == null ? "" : client.player.getName().getString());
        json.addProperty("minecraftServer", getServerKey(client));
        json.addProperty("modVersion", VideoPlayerMain.version);
        return json.toString();
    }

    private static String getServerKey(MinecraftClient client) {
        if (client.isInSingleplayer()) return "singleplayer";
        ServerInfo server = client.getCurrentServerEntry();
        return server == null ? "" : server.address;
    }
}
