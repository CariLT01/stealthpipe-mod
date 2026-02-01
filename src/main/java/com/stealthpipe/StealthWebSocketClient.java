package com.stealthpipe;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Queue<byte[]> queuedSendPackets = new ConcurrentLinkedQueue<>();

    private final AtomicLong lastTick = new AtomicLong(System.nanoTime());

    private final long BATCHING_INTERVAL = StealthPipe.config.PACKET_BATCHING_INTERVAL_MS * 1_000_000L; // 2 milliseconds

    public StealthWebSocketClient(URI serverUri, WebsocketClientType clientType, Channel channel, String gameId) {
        super(serverUri, createHeaders());

        lastTick.set(System.nanoTime());

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

    private void sendQueuedSendPacketsBatched() {
        int PACKET_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB

        while (!this.queuedSendPackets.isEmpty()) {
            // Create a composite buffer to hold the entire batch
            CompositeByteBuf batchBuffer = Unpooled.compositeBuffer();

            try {
                while (!this.queuedSendPackets.isEmpty()) {
                    byte[] packet = this.queuedSendPackets.peek();
                    if (packet == null) break;

                    // Check for size limit: (existing buffer + 4 byte header + packet length)
                    if (batchBuffer.readableBytes() + packet.length + 4 > PACKET_SIZE_LIMIT) {
                        if (batchBuffer.readableBytes() > 0) break;
                        // If single packet > limit, we let it through once so it doesn't clog
                    }

                    // Remove from queue now that we're committed
                    this.queuedSendPackets.poll();

                    // 1. Create a 4-byte buffer for the length header
                    ByteBuf header = Unpooled.copyInt(packet.length);
                    // 2. Wrap the existing packet array (no copy!)
                    ByteBuf body = Unpooled.wrappedBuffer(packet);

                    // Add to composite (true = advance writer index)
                    batchBuffer.addComponents(true, header, body);
                }

                if (batchBuffer.readableBytes() > 0) {
                    // Flatten once for the WebSocket send
                    byte[] flatBatch = new byte[batchBuffer.readableBytes()];
                    batchBuffer.readBytes(flatBatch);

                    this.writeLock.lock();
                    try {
                        ModState.outboundPPSCounter.getAndAdd(1);
                        super.send(flatBatch);
                    } finally {
                        this.writeLock.unlock();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Batching error: ", e);
                this.queuedSendPackets.clear();
            } finally {
                batchBuffer.release(); // Free memory
            }
        }
    }

    private void sendQueuedSendPackets() {
        if (StealthPipe.config.ENABLE_BATCHED_PACKETS) {
            this.sendQueuedSendPacketsBatched();
        } else {
            // We use a Composite buffer to avoid copying data until the last possible second
            CompositeByteBuf composite = Unpooled.compositeBuffer();

            try {
                while (!this.queuedSendPackets.isEmpty()) {
                    byte[] data = this.queuedSendPackets.poll();
                    if (data == null) break;

                    // Add 4-byte length + payload
                    ByteBuf header = Unpooled.copyInt(data.length);
                    ByteBuf body = Unpooled.wrappedBuffer(data);

                    // 'true' means update the writer index immediately
                    composite.addComponents(true, header, body);
                }

                if (composite.readableBytes() > 0) {
                    // Since super.send(byte[]) needs an array, we have to flatten it once here
                    byte[] flat = new byte[composite.readableBytes()];
                    composite.readBytes(flat);

                    this.writeLock.lock();
                    try {
                        super.send(flat);
                    } finally {
                        this.writeLock.unlock();
                    }
                }
            } finally {
                // Netty buffers are reference-counted. You MUST release or leak memory.
                composite.release();
            }
        }
    }

    private void checkShouldFire() {
        long currentTime = System.nanoTime();
        long previousTick = lastTick.get();

        if (!StealthPipe.config.ENABLE_BATCHED_PACKETS || currentTime - lastTick.get() >= this.BATCHING_INTERVAL) {
            // Reached the target interval
            if (!StealthPipe.config.ENABLE_BATCHED_PACKETS) {
                // Send it immediately
                this.sendQueuedSendPackets();
            } else {
                if (lastTick.compareAndSet(previousTick, currentTime)) {
                    this.sendQueuedSendPackets();
                }
            }


        }
    }

    private void sendPacket(byte[] data) {

        if (!this.isOpen() || !this.connected) {
            LOGGER.warn("Attempt to send a packet when the websocket has already been disconnected");
            this.queuedSendPackets.clear();
            return;
        }

        this.queuedSendPackets.add(data);

        this.checkShouldFire();
    }


    private static Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        // Using the Chrome 143 string we discussed
        headers.put("User-Agent", StealthPipe.USER_AGENT);
        return headers;
    }

    public void firePacketsInQueue() {
        this.checkShouldFire();
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
            //this.writeLock.lock();
            //this.send(packet);
            //this.writeLock.unlock();
            for (byte[] packet : this.queuedPackets) {
                this.sendPacket(packet);
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

                while (this.connected && this.isOpen()) {

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

    private List<byte[]> unpackPacket(byte[] packedData) {
        List<byte[]> packets = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(packedData);

        while (buffer.remaining() >= 4) {
            int packetLength = buffer.getInt();

            if (packetLength < 0 || packetLength > buffer.remaining()) {
                LOGGER.error("Failed to parse packet batch: invalid packet length");
                break;
            }

            byte[] packetData = new byte[packetLength];
            buffer.get(packetData);

            packets.add(packetData);
        }

        return packets;
    }

    private void processMessageClient(byte[] data) {

        // Should fire when the client receives a message

        if (gameChannel.isEmpty()) {
            LOGGER.error("Game channel is empty!");
            throw new IllegalArgumentException("Game channel is empty");
        }

        Channel clientChannel = gameChannel.get();

        ModState.clientThreadExecutor.get().execute(() -> {
            clientChannel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(data));
        });

        // LOGGER.info("Received {} bytes as client", data.length);



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

        String url = String.format(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=%s&host=true&request=%s&version=%s", this.gameId, newString, StealthPipe.config.MOD_VERSION);

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
        ModState.inboundPPSCounter.getAndAdd(1);


        // LOGGER.info("Server received: {}", newString);
        // LOGGER.info("Server got packet of length: {}", data.length);



        if (relayType == WebsocketClientType.CLIENT_TO_RELAY) {

            List<byte[]> packets = this.unpackPacket(data);

            for (byte[] packet : packets) {
                this.processMessageClient(packet);
            }
        } else if (relayType == WebsocketClientType.SERVER_TO_RELAY) {
            List<byte[]> packets = this.unpackPacket(data);
            for (byte[] packet : packets) {
                this.processMessageServer(packet);
            }
        } else {
            // SIGNALING
            // RELAY_TO_CLIENT
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
                    StealthPipe.CLIENT_PROXY.disconnectWithReason("§cStealthPipe connection disconnected.\nThe host may have closed the room. If not, try reconnecting.", 250);
                } else {
                    StealthPipe.CLIENT_PROXY.disconnectWithReason("§cStealthPipe failed to connect.\nCheck room code and try again.\n\nMake sure you are using the latest client version.", 0);
                }
            }



            this.gameChannel.ifPresent(Channel::disconnect);


            LOGGER.info("WS Disconnected client channel");


        } else {

            if (this.relayType == WebsocketClientType.RELAY_SIGNALING) {
                ModState.minecraftServer.get().getPlayerList().broadcastSystemMessage(
                        Component.literal("§8[StealthPipe§8] : §cSignaling connection to relay disconnected. Attempting to reconnect...").withStyle(ChatFormatting.RED),
                        false
                );

                StealthPipe.CLIENT_PROXY.connectToRelay();
                return;
            }

            // It is connection on server

            ModState.channelToWSClient.entrySet().removeIf(entry -> {
                if (entry.getValue() == this) {
                    LOGGER.info("Closed Netty channel on the server, and queued for removal");
                    entry.getKey().disconnect(); // Close the Netty channel
                    return true; // Removes this entry from the map
                }
                return false;
            });

            LOGGER.warn("Could not disconnect channel; WS client not found");


        }

        LOGGER.info("Connection closed");

        this.connected = false;
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

            if (!this.isOpen()) {
                LOGGER.warn("Attempted to send binary data when the websocket is not open or has already closed");
                return;
            }

            if (this.relayType == WebsocketClientType.RELAY_SIGNALING) {
                // Don't batch signaling data
                ModState.outboundPPSCounter.getAndAdd(1);
                super.send(data);
            } else {
                this.sendPacket(data);
            }

        } else {
            LOGGER.info("Queueing {} bytes to be sent", data.length);
            this.queuedPackets.add(data);
        }
    }

}
