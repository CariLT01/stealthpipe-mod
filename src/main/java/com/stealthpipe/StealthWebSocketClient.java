package com.stealthpipe;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StealthWebSocketClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.MOD_ID);
    private final List<byte[]> queuedPackets = new ArrayList<>();
    private boolean connected = false;

    public StealthWebSocketClient(URI serverUri) {
        super(serverUri);


    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        // Logic for when the HTTP Upgrade succeeds

        LOGGER.info("WS Handshake success");
        this.connected = true;

        for (byte[] packet : queuedPackets) {
            this.send(packet);
        }

        queuedPackets.clear();

        ModState.webSocketOpen.set(true);
    }



    private void checkClientUUIDPrefix(byte[] data) {
        String prefix = "CLIENTUUID_";
        String dataAsString = new String(data, java.nio.charset.StandardCharsets.UTF_8);

        if (dataAsString.startsWith(prefix)) {
            try {
                // Extract the part after "CLIENTUUID_"
                String uuidString = dataAsString.substring(prefix.length());

                if (uuidString.isEmpty()) {
                    throw new IllegalArgumentException("UUID is empty");
                }

                if (!ModState.pendingChannelUuid.get().isEmpty()) {
                    LOGGER.warn("One player entry will be dropped");
                }

                ModState.pendingChannelUuid.set(uuidString);

                LOGGER.info("Received new UUID and added it to pending");

                // Immediately process the UUID, unlike what the previous log is saying

                MinecraftServer server = ModState.minecraftServer.get();

                EmbeddedChannel virtualChannel = new EmbeddedChannel();
                ServerConnectionListener listener = server.getConnection();

                ((IConnectionInjector) listener).injectVirtualConnection(virtualChannel);

                LOGGER.info("Successfully injected fake connection into server, UUID {}", uuidString);

                ModState.relayUuidToChannelMap.put(uuidString, virtualChannel);
                ModState.minecraftChannelUuidToRelayUuidMap.put(virtualChannel.id().asLongText(), uuidString);

                ModState.pendingChannelUuid.set("");


            } catch (IllegalArgumentException e) {
                LOGGER.error("Error occured while authenticating player: ", e);
            }
        }
    }



    private void processMessageServer(byte[] data) {
        int offset = 0;


        if (data.length < offset + 36) {
            LOGGER.error("Packet too short to contain UUID. Length: {}", data.length);
            return;
        }

        String uuidString = new String(data, offset, 36, StandardCharsets.UTF_8);

        int headerSize = offset + 36;
        int dataLength = Math.max(0, data.length - headerSize);
        byte[] remainingData = new byte[dataLength];

        if (dataLength > 0) {
            System.arraycopy(data, headerSize, remainingData, 0, dataLength);
        }

        if (!ModState.relayUuidToChannelMap.containsKey(uuidString)) {
            LOGGER.warn("Relay UUID of {} does not have corresponding channel", uuidString);
            return;
        }
        Channel playerChannel = ModState.relayUuidToChannelMap.get(uuidString);

        // Artificial read
        ByteBuf buf = Unpooled.wrappedBuffer(remainingData);

        ModState.minecraftServer.get().execute(() -> {
            playerChannel.pipeline().fireChannelRead(buf);
        });



        // LOGGER.info("[SERVER]: Received binary message, length: {}", remainingData.length);
    }

    private void processMessageClient(byte[] data) {
        Channel clientChannel = ModState.relayClientChannel.get();
        if (clientChannel == null) {
            return;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(data);


        ModState.clientThreadExecutor.get().execute(() -> {
            clientChannel.pipeline().fireChannelRead(buf);
        });

        // LOGGER.info("[CLIENT]: Received binary message, length: {}", data.length);
    }

    @Override
    public void onMessage(ByteBuffer byteBuf) {
        // Logic for when the server sends data to your mod



        byte[] data = new byte[byteBuf.remaining()];
        byteBuf.get(data);

        String newString = new String(data, StandardCharsets.UTF_8);

        // LOGGER.info("Server received: {}", newString);
        // LOGGER.info("Server got packet of length: {}", data.length);



        if (!ModState.isClientConnectingToStealthServer.get()) {
            // Client connecting to server

            if (newString.startsWith("CLIENTUUID_")) {
                checkClientUUIDPrefix(data);
            } else {
                this.processMessageServer(data);
            }
        } else {
            // Server connecting to relay



            this.processMessageClient(data);

        }


    }

    @Override
    public void onMessage(String message) {
        // You can leave this empty if you don't expect text



        LOGGER.warn("Received string message: {}", message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Logic for when the connection ends

        boolean isClient = ModState.isClientConnectingToStealthServer.get();

        if (isClient) {

            // Disconnect the channel

            Channel clientChannel = ModState.relayClientChannel.get();

            clientChannel.eventLoop().execute(() -> {
                clientChannel.disconnect();
            });

            LOGGER.info("Client disconnected channel");


        }

        LOGGER.info("Connection closed");
    }

    @Override
    public void onError(Exception ex) {
        // This is where you put that .displayClientMessage code!

        LOGGER.error("Error occurred in stealth WS: ", ex);


    }

    @Override
    public void send(byte[] data) {



        if (this.connected) {
            // LOGGER.info("Sending {} bytes", data.length);
            super.send(data);
        } else {
            LOGGER.info("Queueing {} bytes to be sent", data.length);
            this.queuedPackets.add(data);
        }
    }

}
