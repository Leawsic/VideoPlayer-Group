package com.github.squi2rel.vp.group;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

public class GroupConnection implements WebSocket.Listener {
    private final HttpClient client = HttpClient.newHttpClient();
    private WebSocket webSocket;
    private volatile boolean connected;
    private String helloJson;

    public void connect(URI uri) {
        connect(uri, null);
    }

    public void connect(URI uri, String helloJson) {
        disconnect();
        this.helloJson = helloJson;
        client.newWebSocketBuilder().buildAsync(uri, this).exceptionally(e -> {
            onError(null, e);
            return null;
        });
    }

    public void disconnect() {
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnect");
        }
        webSocket = null;
        connected = false;
    }

    public void send(String json) {
        WebSocket socket = webSocket;
        if (!connected || socket == null) return;
        socket.sendText(json, true);
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        connected = true;
        MinecraftClient.getInstance().execute(() -> {
            message("已连接到房间服务器", Formatting.GREEN);
            if (helloJson != null) send(helloJson);
        });
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (last) {
            String json = data.toString();
            MinecraftClient.getInstance().execute(() -> GroupPacketHandler.handle(json));
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        if (this.webSocket == webSocket) this.webSocket = null;
        MinecraftClient.getInstance().execute(() -> message("已断开房间服务器连接", Formatting.YELLOW));
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        if (this.webSocket == webSocket) this.webSocket = null;
        LOGGER.error("Group connection error", error);
        MinecraftClient.getInstance().execute(() -> message("房间服务器连接错误: " + error.getMessage(), Formatting.RED));
    }

    private static void message(String text, Formatting formatting) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(formatting), false);
        }
    }
}
