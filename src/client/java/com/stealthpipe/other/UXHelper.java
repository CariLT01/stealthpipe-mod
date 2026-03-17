package com.stealthpipe.other;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class UXHelper {

    public static void _sendSystemMessage(String message, ChatFormatting style) {
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(message).withStyle(style),
                    false
            );
        });


    }

    public static void sendStealthPipeSystemMessage(String message) {
        assert Minecraft.getInstance().player != null;

        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(String.format("§8[StealthPipe] : §7%s", message)),
                    false
            );
        });
    }


    public static void sendSystemMessageComponent(Component message, ChatFormatting style) {
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().player.displayClientMessage(
                    message,
                    false
            );
        });


    }

}
