package com.stealthpipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DisconnectHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    public static void showDisconnectMessageAndRetry(String reason) {
        String realReason = Objects.equals(reason, "") ? "None provided" : reason;

        LOGGER.info("attempt retry");
        ModState.minecraftServer.get().getPlayerList().broadcastSystemMessage(
                Component.literal(String.format("§8[StealthPipe§8] : §cSignaling connection to relay disconnected. Attempting to reconnect... (error code: %s)", realReason)).withStyle(ChatFormatting.RED),
                false
        );

        StealthPipe.CLIENT_PROXY.connectToRelay();
    }

    public static void showClientDisconnectMessage(boolean gotMessages, String reason) {
        String realReason = !Objects.equals(reason, "") ? reason : "None provided";

        if (StealthPipe.CLIENT_PROXY != null) {
            if (gotMessages) {
                StealthPipe.CLIENT_PROXY.disconnectWithReason(
                        String.format("§cStealthPipe connection disconnected.\nThe host may have closed the room. If not, try reconnecting.\n(error code: %s)", realReason), 250);
            } else {
                StealthPipe.CLIENT_PROXY.disconnectWithReason(
                        String.format("§cStealthPipe failed to connect.\nCheck room code and try again.\n\nMake sure you are using the latest client version.\n(error code: %s)", realReason), 0);
            }
        }
    }
}
