package com.stealthpipe;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class WebSocketHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);



    public static void connectToServer() throws Exception {

        LOGGER.info("Connecting to stealth websocket...");

        String gameId = ModState.gameId.get();

        if (gameId.isEmpty()) {
            throw new IllegalArgumentException("Attempt to connect to websocket when gameId is empty");
        }

        boolean isClient = ModState.isClientConnectingToStealthServer.get();

        String hostQuery = !isClient ? "&host=true" : "";

        URI uri = new URI(StealthPipe.config.RELAY_IP.replace("https://", "wss://").replace("http://", "ws://") + "/join?id=" + gameId + hostQuery);

        StealthWebSocketClient wsClient = new StealthWebSocketClient(uri, WebsocketClientType.RELAY_SIGNALING, gameId);
        wsClient.connect();

        ModState.relayClient.set(wsClient);
        ModState.webSocketOpen.set(true);

        LOGGER.info("Connected to external WS, started signaling");
    }
}
