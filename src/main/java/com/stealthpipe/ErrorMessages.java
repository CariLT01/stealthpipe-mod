package com.stealthpipe;

import java.util.Map;
import static java.util.Map.entry;

public class ErrorMessages {
    public static final Map<String, String> MESSAGES = Map.ofEntries(
            entry("HOST_DISCONNECTED", "The host has left the game."),
            entry("HIGH_BANDWIDTH", "You are sending too much data. Try lowering your render distance and try again."),
            entry("HIGH_USAGE", "You are sending too much data. Please wait and try again."),
            entry("WSS_READ_FAILED", "Socket read failed. Please try reconnecting."),
            entry("WSS_WRITE_FAILED", "Socket write failed. Please try reconnecting."),
            entry("WSS_IDLE", "Your room has been empty for too long."),
            entry("ROOM_NOT_FOUND", "Room does not exist. Check room code and try again."),
            entry("ROOM_NO_HOST", "You have connected before the host has. Please wait and try again."),
            entry("ROOM_BAD_STATE", "The room is not in a state that allows joining."),
            entry("INTERNAL_ERROR", "An unexpected error occurred. Please try again."),
            entry("INVALID_REQUEST_ID", "Couldn't correlate the joining connection with an existing connection. Please try again."),
            entry("PAIR_LINK_FAILED", "Couldn't pair the client and host connections. Please try again."),
            entry("INVALID_ACTION", "This action is forbidden."),
            entry("REUSE_TOKEN_PRESENTED", "Another client has reused your room code. Please restart the session to generate a new code."),
            entry("UNKNOWN", "An unknown error occurred. Please try again."),
            entry("CLIENT_DISCONNECTED", "The client has disconnected."),
            entry("UNSPECIFIED", "Disconnect reason has not been specified"),
            entry("PACKET_TOO_LARGE", "A packet you sent was too large."),
            entry("SIGNAL_DISCONNECTED", "The main room connection has disconnected."),
            entry("BAD_REQUEST", "The client has sent an unexpected request."),
            entry("WRTC_DISCONNECTED", "The connection to the other client has disconnected."),
            entry("ABNORMAL_DISCONNECT", "Disconnected unexpectedly. Please check your internet connection and try again."),
            entry("INVALID_VERSION", "This version of the mod is invalid!"),
            entry("OUTDATED_CLIENT", "Outdated client. Please update to the latest version of StealthPipe."),
            entry("UNSUPPORTED_CLIENT", "Unsupported client version. Your client is too new for this relay."),
            entry("ROOM_RECENTLY_CLOSED", "This room was recently closed.")
    );
    public static final String DEFAULT_MESSAGE = "Disconnected.";

    public static String errorReasonToMessage(String reason) {
        return MESSAGES.getOrDefault(reason, DEFAULT_MESSAGE);
    }
}
