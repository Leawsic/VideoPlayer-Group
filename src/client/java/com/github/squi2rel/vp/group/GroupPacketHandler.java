package com.github.squi2rel.vp.group;

import static com.github.squi2rel.vp.VideoPlayerMain.LOGGER;

public class GroupPacketHandler {
    public static void handle(String json) {
        LOGGER.info("Group message: {}", json);
    }
}
