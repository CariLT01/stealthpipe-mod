package com.stealthpipe;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class StealthWebSocketClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);
    private final List<byte[]> queuedPackets = new ArrayList<>();
    private boolean connected = false;
    private final WebsocketClientType relayType;
    private final URI relayUrl;
    private final String gameId;
    private boolean gotMessages = false;

    private final ReentrantLock writeLock = new ReentrantLock();

    private Optional<Channel> gameChannel = Optional.empty();

    public StealthWebSocketClient(URI serverUri, WebsocketClientType clientType, Channel channel, String gameId) {
        super(serverUri, createHeaders());

        if (clientType == WebsocketClientType.RELAY_SIGNALING) {
            throw new IllegalArgumentException("Relay signaling cannot have channel argument");
        }

        this.relayType = clientType;
        this.gameChannel = Optional.of(channel);
        this.relayUrl = serverUri;
        this.gameId = gameId;
        this.gotMessages = false;

        LOGGER.info("New WS client created");


    }

    private static Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        // Using the Chrome 143 string we discussed
        headers.put("User-Agent", StealthPipe.USER_AGENT);
        return headers;
    }

    public StealthWebSocketClient(URI serverUri, WebsocketClientType type, String gameId) {
        super(serverUri);

        if (ModState.isClientConnectingToStealthServer.get()) {
            LOGGER.warn("Instructed to open SIGNALING socket when state is client!");
        }

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
                this.writeLock.lock();
                this.send(packet);
                this.writeLock.unlock();
            }

            queuedPackets.clear();
        }

        this.keepAliveLoop();


    }

    private void keepAliveLoop() {
        if (this.relayType == WebsocketClientType.RELAY_SIGNALING) {

            String keepAliveString = "keep-alive";
            byte[] byteArray = keepAliveString.getBytes();

            new Thread(() -> {

                LOGGER.info("Starting keep-alive");

                int numberOfErrorsDetected = 0;

                while (this.connected) {

                    try {
                        Thread.sleep(1000);
                        this.send(byteArray);
                    } catch (WebsocketNotConnectedException e) {
                        LOGGER.warn("Socket disconnected");
                        break;
                    }
                    catch (Exception e) {
                        LOGGER.error("Keep-alive signal failed to get sent: ", e);

                        numberOfErrorsDetected += 1;

                        if (numberOfErrorsDetected > 10) {
                            LOGGER.error("more than 10 errors detected, keep-alive loop will be killed reducing connection reliability");
                            break;
                        }
                    }


                }

                LOGGER.info("keep-alive stopped");
            }).start();
        } else {
            LOGGER.info("Not signaling websocket, keep-alive not sent");
        }
    }


    private void processMessageServer(byte[] data) {
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
            LOGGER.error("Game channel is empty!");
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


        this.gotMessages = true;

        byte[] data = new byte[byteBuf.remaining()];
        byteBuf.get(data);

        ModState.inboundData.getAndAdd(data.length);
        ModState.inboundBandwidthCounter.getAndAdd(data.length);



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
    public void close() {
        LOGGER.info("Close called on WS client");
        super.close();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Logic for when the connection ends

        boolean isClient = ModState.isClientConnectingToStealthServer.get();

        if (isClient) {

            // Disconnect the channel


            if (StealthPipe.CLIENT_PROXY != null) {
                if (this.gotMessages) {
                    StealthPipe.CLIENT_PROXY.disconnectWithReason("§cStealthPipe connection disconnected unexpectedly.\nTry reconnecting.");
                } else {
                    StealthPipe.CLIENT_PROXY.disconnectWithReason("§cStealthPipe failed to connect.\nCheck room code and try again.\n\nMake sure you are using the latest client version.");
                }
            }



            this.gameChannel.ifPresent(Channel::disconnect);


            LOGGER.info("WS Disconnected client channel");


        } else {

            if (this.relayType == WebsocketClientType.RELAY_SIGNALING) {
                ModState.minecraftServer.get().getPlayerList().broadcastSystemMessage(
                        Component.literal("§8[StealthPipe§8] : §cSignaling connection to relay disconnected. Room closed.\n§7You need to create a new room by rejoining into this world and clicking on opening to LAN.").withStyle(ChatFormatting.RED),
                        false
                );
            }


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

        ModState.outboundData.getAndAdd(data.length);
        ModState.outboundBandwidthCounter.getAndAdd(data.length);

        if (this.connected) {
            /* this is some code: if (ModState.isClientConnectingToStealthServer.get()) {
                LOGGER.info("Sending {} bytes", data.length);
            } */



            this.writeLock.lock();
            super.send(data);
            this.writeLock.unlock();
        } else {
            LOGGER.info("Queueing {} bytes to be sent", data.length);
            this.queuedPackets.add(data);
        }
    }

}
