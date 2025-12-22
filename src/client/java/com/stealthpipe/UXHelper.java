package com.stealthpipe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class UXHelper {

    public static void sendSystemMessage(String message, ChatFormatting style) {
        assert Minecraft.getInstance().player != null;
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(message).withStyle(style),
                    false
            );
        });


    }

}
