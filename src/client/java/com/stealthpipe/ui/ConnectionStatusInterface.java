package com.stealthpipe.ui;

import com.stealthpipe.StealthPipe;
import net.minecraft.client.Minecraft;
/*? if >=26.1 { */
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*//*?} else { */
import net.minecraft.client.gui.GuiGraphics;
/*?}*/
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ConnectionStatusInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    public static final List<String> connectionStatuses = new ArrayList<>();

    /**
     * Resizes the connectionStatuses array to a certain length.
     * <p>
     * If the length exceeds the current size, it will add elements with empty strings ("").
     * If the length is lower than the current size, elements at the tail will be removed first.
     * If the length is the same as the current size, nothing will be done.
     *
     * @param length The new length of the array
     */
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

    /**
     * Sets the status text at the connection status index. It will automatically resize the array if necessary.
     *
     * @param text The text to show at the specified index. Supports Minecraft color codes, beginnings with '§'.
     * @param index The index to draw the text at.
     */
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

    /**
     * Function that renders the connection status texts.
     *
     * @param guiGraphics GuiGraphics object provided by whatever callback or event was used.
     */

    /*? if <= 1.21.11 {*/
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
    /*?} else {*/
    /*public static void renderConnectionStatusText(GuiGraphicsExtractor guiGraphics) {
        if (!StealthPipe.config.SHOW_CONNECT_INFO) return;

        // LOGGER.info("Render");
        int centerX = guiGraphics.guiWidth() / 2;
        for (int i = 0; i < connectionStatuses.size(); i++) {
            String text = connectionStatuses.get(i);
            int yOffset = 20 + i * 12;
            guiGraphics.centeredText(Minecraft.getInstance().font, Component.literal(text), centerX, yOffset, 0xFFFFFFFF);
        }

    }
    *//*?}*/


}
