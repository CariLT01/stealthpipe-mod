package com.stealthpipe.other;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class UXHelper {

    private static void _sendMessageMultiVersion(Component component) {
        /*? if <26.1 { */
        Minecraft.getInstance().player.displayClientMessage(
                    component,
                    false
        );
        /*? } else { */
        /*Minecraft.getInstance().player.sendSystemMessage(
                component

        );
        *//*? } */
    }

    public static void _sendSystemMessage(String message, ChatFormatting style) {
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().execute(() -> {
            _sendMessageMultiVersion(Component.literal(message).withStyle(style));
        });


    }

    /**
     * Sends a message in the chat with the '[StealthPipe]:' prefix.
     * **Note**: this only works on the client
     *
     * @param message The message to append
     */
    public static void sendStealthPipeSystemMessage(String message) {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        Minecraft.getInstance().execute(() -> {
            _sendMessageMultiVersion(Component.literal(String.format("§8[StealthPipe] : §7%s", message)));
        });
    }

    /**
     * Sends a message with a custom Component object. This function does not append a prefix. This allows custom
     * elements like some clickable text with custom action.
     *
     * @param message The Component to send
     * @param style ChatFormatting style (not used)
     */

    public static void sendSystemMessageComponent(Component message, ChatFormatting style) {
        if (Minecraft.getInstance().player == null) return;
        Minecraft.getInstance().execute(() -> {
            _sendMessageMultiVersion(message);
        });


    }

}
