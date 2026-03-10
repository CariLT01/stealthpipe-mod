package com.stealthpipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisconnectHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    public static void showDisconnectMessageAndRetry() {
        LOGGER.info("attempt retry");
        ModState.minecraftServer.get().getPlayerList().broadcastSystemMessage(
                Component.literal("§8[StealthPipe§8] : §cSignaling connection to relay disconnected. Attempting to reconnect...").withStyle(ChatFormatting.RED),
                false
        );

        StealthPipe.CLIENT_PROXY.connectToRelay();
    }

    public static void showClientDisconnectMessage(boolean gotMessages) {
        if (StealthPipe.CLIENT_PROXY != null) {
            if (gotMessages) {
                StealthPipe.CLIENT_PROXY.disconnectWithReason("§cStealthPipe connection disconnected.\nThe host may have closed the room. If not, try reconnecting.", 250);
            } else {
                StealthPipe.CLIENT_PROXY.disconnectWithReason("§cStealthPipe failed to connect.\nCheck room code and try again.\n\nMake sure you are using the latest client version.", 0);
            }
        }
    }
}
