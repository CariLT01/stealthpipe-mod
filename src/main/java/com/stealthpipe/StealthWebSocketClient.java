package com.stealthpipe;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StealthWebSocketClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);
    private final List<byte[]> queuedPackets = new ArrayList<>();
    private boolean connected = false;
    private final WebsocketClientType relayType;
    private final URI relayUrl;
    private final String gameId;

    private Optional<Channel> gameChannel = Optional.empty();

    public StealthWebSocketClient(URI serverUri, WebsocketClientType clientType, Channel channel, String gameId) {


        super(serverUri);

        if (clientType == WebsocketClientType.RELAY_SIGNALING) {
            throw new IllegalArgumentException("Relay signaling cannot have channel argument");
        }

        this.relayType = clientType;
        this.gameChannel = Optional.of(channel);
        this.relayUrl = serverUri;
        this.gameId = gameId;

        LOGGER.info("New WS client created");


    }


    public StealthWebSocketClient(URI serverUri, WebsocketClientType type, String gameId) {
        super(serverUri);

        if (type != WebsocketClientType.RELAY_SIGNALING) {
            throw new IllegalArgumentException("Must be relay signaling");
        }

        this.relayType = WebsocketClientType.RELAY_SIGNALING;
        this.relayUrl = serverUri;
        this.gameId = gameId;
    }



    @Override
    public void onOpen(ServerHandshake handshakeData) {
        // Logic for when the HTTP Upgrade succeeds

        LOGGER.info("Disabling Nagle's algorithm");
        setTcpNoDelay(true);

        LOGGER.info("WS Handshake success");
        this.connected = true;



        if (relayType == WebsocketClientType.CLIENT_TO_RELAY) {
            ModState.webSocketOpen.set(true);
        }

        if (relayType == WebsocketClientType.CLIENT_TO_RELAY || relayType == WebsocketClientType.SERVER_TO_RELAY) {
            for (byte[] packet : queuedPackets) {
                this.send(packet);
            }

            queuedPackets.clear();
        }


    }



    /*private void checkClientUUIDPrefix(byte[] data) {
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
                ModState.minecraftChannelUuidToRelayUuidMap.put(virtualChannel, uuidString);
                ModState.reverseChannelToRelayUUIDMap.put(uuidString, virtualChannel);

                ModState.pendingChannelUuid.set("");


            } catch (IllegalArgumentException e) {
                LOGGER.error("Error occured while authenticating player: ", e);
            }
        }
    }*/



    /*private boolean checkClientClosePrefix(byte[] data) {
        String prefix = "CLOSECONNECTION_";
        String dataAsString = new String(data, java.nio.charset.StandardCharsets.UTF_8);

        if (dataAsString.startsWith(prefix)) {
            // Extract the part after "CLOSECONNECTION_"
            String uuidString = dataAsString.substring(prefix.length());

            Channel virtualChannel = ModState.reverseChannelToRelayUUIDMap.get(uuidString);

            if (virtualChannel == null) {
                LOGGER.error("Cannot find disconnect uuid");

                return true;
            }

            virtualChannel.disconnect();

            LOGGER.info("Disconnected channel per request");
        }

        return false;
    }*/


    private void processMessageServer(byte[] data) {

        // LOGGER.info("Received {} bytes as server", data.length);

        /*int offset = 0;


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



        // LOGGER.info("[SERVER]: Received binary message, length: {}", remainingData.length);*/


        // COnnection type is CLIENT_TO_SERVER (where we are the server)
        // Fire in the current player channel

        if (this.gameChannel.isEmpty()) {
            throw new IllegalArgumentException("Game channel is empty");
        }

        ModState.minecraftServer.get().execute(() -> {
            this.gameChannel.get().pipeline().fireChannelRead(Unpooled.wrappedBuffer(data));
        });




    }

    private void processMessageClient(byte[] data) {

        // LOGGER.info("Received {} bytes as client", data.length);

        ByteBuf buf = Unpooled.wrappedBuffer(data);

        if (gameChannel.isEmpty()) {
            throw new IllegalArgumentException("Game channel is empty");
        }

        Channel clientChannel = gameChannel.get();

        ModState.clientThreadExecutor.get().execute(() -> {
            clientChannel.pipeline().fireChannelRead(buf);
        });

        // LOGGER.info("[CLIENT]: Received binary message, length: {}", data.length);
    }

    private void processMessageSignaling(byte[] data) {
        String newString = new String(data, StandardCharsets.UTF_8);

        if (!newString.startsWith("REQUESTCONNECTION_")) return;

        // We need to create a fake channel

        MinecraftServer server = ModState.minecraftServer.get();

        EmbeddedChannel virtualChannel = new EmbeddedChannel();
        ServerConnectionListener listener = server.getConnection();

        ((IConnectionInjector) listener).injectVirtualConnection(virtualChannel);

        String url = String.format(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=%s&host=true&request=%s", this.gameId, newString);

        StealthWebSocketClient newClient = new StealthWebSocketClient(URI.create(url), WebsocketClientType.SERVER_TO_RELAY, virtualChannel, gameId);
        newClient.connect();

        ModState.channelToWSClient.put(virtualChannel, newClient);

        LOGGER.info("Created new channel to relay");
    }

    @Override
    public void onMessage(ByteBuffer byteBuf) {
        // Logic for when the server sends data to your mod



        byte[] data = new byte[byteBuf.remaining()];
        byteBuf.get(data);



        // LOGGER.info("Server received: {}", newString);
        // LOGGER.info("Server got packet of length: {}", data.length);



        if (relayType == WebsocketClientType.CLIENT_TO_RELAY) {
            this.processMessageClient(data);
        } else if (relayType == WebsocketClientType.SERVER_TO_RELAY) {
            this.processMessageServer(data);
        } else {
            this.processMessageSignaling(data);
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

            this.gameChannel.ifPresent(Channel::disconnect);


            LOGGER.info("WS Disconnected client channel");


        }

        LOGGER.info("Connection closed");
    }

    @Override
    public void onError(Exception ex) {


        LOGGER.error("Error occurred in stealth WS: ", ex);

        if (!ModState.isClientConnectingToStealthServer.get()) {
            MinecraftServer server = ModState.minecraftServer.get();

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[StealthPipe] Error occurred in tunnel. Please restart the world and create a new session.").withStyle(ChatFormatting.RED),
                    false
            );
        }

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
