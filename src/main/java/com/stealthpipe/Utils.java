package com.stealthpipe;

import java.net.URI;
import java.util.Optional;

public class Utils {

    /**
     * Automatically formats some arguments into an HTTP websocket join link.
     *
     * @param gameId Room ID
     * @param host Is it the host connecting?
     * @param requestId The request ID, if applicable, for WSS linking. It's an Optional field.
     * @param clientSignal Is it a signal connection from the client?
     * @return Returns a URI object used for connecting via the WebSocket client
     */

    public static URI formatWebSocketJoinURL(String gameId, boolean host, Optional<String> requestId, boolean clientSignal) {
        String hostQuery = host ? "&host=true" : "";
        String requestIdQuery = requestId.map(s -> "&request=" + s).orElse("");
        String clientSignQuery = clientSignal ? "&signal=true" : "";

        return URI.create(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=" + gameId + "&version=" + StealthPipe.MOD_VERSION + hostQuery + requestIdQuery + clientSignQuery);
    }
}
