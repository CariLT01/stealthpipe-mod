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
        String reasonPresented = ErrorMessages.errorReasonToMessage(reason);


        LOGGER.info("attempt retry");
        ModState.minecraftServer.get().getPlayerList().broadcastSystemMessage(
                Component.literal(String.format("§8[StealthPipe§8] : \n§cSignaling connection to relay disconnected.\n\n§7%s\n§8(error code: %s)\n\n§aAttempting to reconnect...", reasonPresented, realReason)).withStyle(ChatFormatting.RED),
                false
        );

        StealthPipe.CLIENT_PROXY.connectToRelay();
    }

    public static void showClientDisconnectMessage(boolean gotMessages, String reason) {
        String realReason = !Objects.equals(reason, "") ? reason : "None provided";
        String reasonPresented = ErrorMessages.errorReasonToMessage(reason);


        if (StealthPipe.CLIENT_PROXY != null) {
            if (gotMessages) {
                StealthPipe.CLIENT_PROXY.disconnectWithReason(
                        String.format("§cStealthPipe connection disconnected.\n\n§7%s\n§8(error code: %s)", reasonPresented, realReason), 250);
            } else {
                StealthPipe.CLIENT_PROXY.disconnectWithReason(
                        String.format("§cStealthPipe failed to connect.\n\n§7%s\n§8(error code: %s)", reasonPresented, realReason), 0);
            }
        }
    }
}
