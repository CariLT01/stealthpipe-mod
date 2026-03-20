package com.stealthpipe.ui;

import com.stealthpipe.StealthPipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ConnectionStatusInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    public static final List<String> connectionStatuses = new ArrayList<>();

    public static void setConnectionStatusLength(int length) {
        LOGGER.info("Resize array to length: {}", length);
        if (length > connectionStatuses.size()) {
            for (int i = 0; i < (length - connectionStatuses.size()); i++) {
                connectionStatuses.add("");
            }
        } else if (length < connectionStatuses.size()) {
            connectionStatuses.subList(length, connectionStatuses.size()).clear();
        }
    }
    public static void setConnectionStatusText(String text, int index) {
        try {
            if (index >= connectionStatuses.size()) {
                setConnectionStatusLength(index + 1);
            }

            while (connectionStatuses.size() <= index) {
                LOGGER.warn("Added additional item to array");
                connectionStatuses.add("");
            }

            LOGGER.info("Set connection status: {} at index {}", text, index);

            connectionStatuses.set(index, text);
        } catch (Throwable t) {
            LOGGER.error("Failed to set connection status", t);
        }

    }

    public static void renderConnectionStatusText(GuiGraphics guiGraphics) {
        if (!StealthPipe.config.SHOW_CONNECT_INFO) return;

        // LOGGER.info("Render");
        int centerX = guiGraphics.guiWidth() / 2;
        for (int i = 0; i < connectionStatuses.size(); i++) {
            String text = connectionStatuses.get(i);
            int yOffset = 20 + i * 12;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, Component.literal(text), centerX, yOffset, 0xFFFFFFFF);
        }

    }

}
