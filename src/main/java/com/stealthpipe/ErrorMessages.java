package com.stealthpipe;

import net.minecraft.network.chat.Component;

import java.util.Map;
import static java.util.Map.entry;

public class ErrorMessages {
    public static final Map<String, String> MESSAGES = Map.ofEntries(
            entry("HOST_DISCONNECTED", Component.translatable("error.stealthpipe.hostDisconnected").getString()),
            entry("HIGH_BANDWIDTH", Component.translatable("error.stealthpipe.highBandwidth").getString()),
            entry("HIGH_USAGE", Component.translatable("error.stealthpipe.highUsage").getString()),
            entry("WSS_READ_FAILED", Component.translatable("error.stealthpipe.wssReadFailed").getString()),
            entry("WSS_WRITE_FAILED", Component.translatable("error.stealthpipe.wssWriteFailed").getString()),
            entry("WSS_IDLE", Component.translatable("error.stealthpipe.wssIdle").getString()),
            entry("ROOM_NOT_FOUND", Component.translatable("error.stealthpipe.roomNotFound").getString()),
            entry("ROOM_NO_HOST", Component.translatable("error.stealthpipe.roomNoHost").getString()),
            entry("ROOM_BAD_STATE", Component.translatable("error.stealthpipe.roomBadState").getString()),
            entry("INTERNAL_ERROR", Component.translatable("error.stealthpipe.internalError").getString()),
            entry("INVALID_REQUEST_ID", Component.translatable("error.stealthpipe.invalidRequestId").getString()),
            entry("PAIR_LINK_FAILED", Component.translatable("error.stealthpipe.pairLinkFailed").getString()),
            entry("INVALID_ACTION", Component.translatable("error.stealthpipe.invalidAction").getString()),
            entry("REUSE_TOKEN_PRESENTED", Component.translatable("error.stealthpipe.reuseTokenPresented").getString()),
            entry("UNKNOWN", Component.translatable("error.stealthpipe.unknown").getString()),
            entry("CLIENT_DISCONNECTED", Component.translatable("error.stealthpipe.clientDisconnected").getString()),
            entry("UNSPECIFIED", Component.translatable("error.stealthpipe.unspecified").getString()),
            entry("PACKET_TOO_LARGE", Component.translatable("error.stealthpipe.packetTooLarge").getString()),
            entry("SIGNAL_DISCONNECTED", Component.translatable("error.stealthpipe.signalDisconnected").getString()),
            entry("BAD_REQUEST", Component.translatable("error.stealthpipe.badRequest").getString()),
            entry("WRTC_DISCONNECTED", Component.translatable("error.stealthpipe.wrtcDisconnected").getString()),
            entry("ABNORMAL_DISCONNECT", Component.translatable("error.stealthpipe.abnormalDisconnect").getString()),
            entry("INVALID_VERSION", Component.translatable("error.stealthpipe.invalidVersion").getString()),
            entry("OUTDATED_CLIENT", Component.translatable("error.stealthpipe.outdatedClient").getString()),
            entry("UNSUPPORTED_CLIENT", Component.translatable("error.stealthpipe.unsupportedClient").getString()),
            entry("ROOM_RECENTLY_CLOSED", Component.translatable("error.stealthpipe.roomRecentlyClosed").getString())
    );
    public static final String DEFAULT_MESSAGE = "Disconnected.";

    public static String errorReasonToMessage(String reason) {
        return MESSAGES.getOrDefault(reason, DEFAULT_MESSAGE);
    }
}
