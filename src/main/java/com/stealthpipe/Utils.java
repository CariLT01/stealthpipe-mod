package com.stealthpipe;

import java.net.URI;
import java.util.Optional;

public class Utils {
    public static URI formatWebSocketJoinURL(String gameId, boolean host, Optional<String> requestId, boolean clientSignal) {
        String hostQuery = host ? "&host=true" : "";
        String requestIdQuery = requestId.map(s -> "&request=" + s).orElse("");
        String clientSignQuery = clientSignal ? "&signal=true" : "";

        return URI.create(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=" + gameId + "&version=" + StealthPipe.MOD_VERSION + hostQuery + requestIdQuery + clientSignQuery);
    }
}
