package com.github.squi2rel.vp.group;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.stream.Collectors;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

public class GroupPacketHandler {
    private static final Gson gson = new Gson();

    public static void handle(String json) {
        LOGGER.info("Group message: {}", json);
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String type = string(object, "type");
            switch (type) {
                case "room_created" -> roomCreated(object);
                case "room_list" -> roomList(object);
                case "room_state" -> roomState(object);
                case "member_joined" -> memberJoined(object);
                case "member_left" -> memberLeft(object);
                case "room_disbanded" -> roomDisbanded(object);
                case "error" -> error(object);
                default -> LOGGER.info("Unknown group message type: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle group message", e);
            message("无法解析房间服务器消息", Formatting.RED);
        }
    }

    private static void roomCreated(JsonObject object) {
        message("房间已创建: " + string(object, "roomName") + " (" + string(object, "roomId") + ")", Formatting.GREEN);
    }

    private static void roomList(JsonObject object) {
        JsonArray rooms = object.has("rooms") && object.get("rooms").isJsonArray() ? object.getAsJsonArray("rooms") : new JsonArray();
        if (rooms.isEmpty()) {
            message("当前没有可加入的房间", Formatting.GOLD);
            return;
        }
        String text = rooms.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(room -> "%s - %s".formatted(string(room, "roomId"), string(room, "roomName")))
                .collect(Collectors.joining("\n"));
        message("房间列表:\n" + text, Formatting.GOLD);
    }

    private static void roomState(JsonObject object) {
        GroupRoomState state = object.has("state") && object.get("state").isJsonObject()
                ? gson.fromJson(object.get("state"), GroupRoomState.class)
                : gson.fromJson(object, GroupRoomState.class);
        MinecraftClient client = MinecraftClient.getInstance();
        GroupClient.updateState(state, client.player == null ? "" : client.player.getUuidAsString());
        message("已更新房间状态: " + GroupClient.roomName + "，成员 " + GroupClient.members.size() + " 人", Formatting.GREEN);
    }

    private static void memberJoined(JsonObject object) {
        message("成员加入房间: " + string(object, "playerName"), Formatting.GREEN);
    }

    private static void memberLeft(JsonObject object) {
        message("成员离开房间: " + string(object, "playerName"), Formatting.YELLOW);
    }

    private static void roomDisbanded(JsonObject object) {
        GroupClient.clearRoom();
        message("房间已解散", Formatting.YELLOW);
    }

    private static void error(JsonObject object) {
        message("房间服务器错误: " + string(object, "message"), Formatting.RED);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static void message(String text, Formatting formatting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(formatting), false);
        }
    }
}
