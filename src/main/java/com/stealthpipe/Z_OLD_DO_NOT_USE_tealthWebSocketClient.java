package com.stealthpipe;


import com.stealthpipe.connection.DisconnectHandler;
import com.stealthpipe.connection.game.WebRTCGameConnection;
import com.stealthpipe.connection.misc.RoundTripTimeMonitor;
import com.stealthpipe.enums.SignalingMessageType;
import com.stealthpipe.enums.ConnectionDisconnectReason;
import com.stealthpipe.enums.WebsocketClientType;
import com.stealthpipe.interfaces.IConnectionInjector;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Z_OLD_DO_NOT_USE_tealthWebSocketClient extends WebSocketClient {

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

    private final AtomicLong sentBegin = new AtomicLong(System.nanoTime());
    private final AtomicLong sentEnd = new AtomicLong(System.nanoTime());

    private final AtomicInteger pingMilliseconds = new AtomicInteger(0);

    // alert time, allow alerting 2 minutes after connection instead of 15 at first alert
    private final AtomicLong lastAlertTime = new AtomicLong(System.currentTimeMillis() - 13 * 60 * 1000);

    private final long BATCHING_INTERVAL = StealthPipe.config.PACKET_BATCHING_INTERVAL_MS * 1_000_000L; // 2 milliseconds

    private final int DEFAULT_PING_INTERVAL = 300 * 1000; // 5 minutes

    private List<Consumer<byte[]>> hookedEvents = new ArrayList<>();

    private final RoundTripTimeMonitor RTTMonitor = new RoundTripTimeMonitor(
            10,
            120,
            50
    );



    public Z_OLD_DO_NOT_USE_tealthWebSocketClient(URI serverUri, WebsocketClientType clientType, Channel channel, String gameId) {
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

    public Z_OLD_DO_NOT_USE_tealthWebSocketClient(URI serverUri, WebsocketClientType type, String gameId) {
        super(serverUri);

        if (ModState.isClientConnectingToStealthServer.get()) {
            LOGGER.warn("Instructed to open SIGNALING socket when state is client!");
        }

        if (type != WebsocketClientType.RELAY_SIGNALING && type != WebsocketClientType.CLIENT_SIGNALING) {
            throw new IllegalArgumentException("Must be relay signaling");
        }

        this.relayType = type;
        this.relayUrl = serverUri;
        this.gameId = gameId;
    }

    public void hookOnMessage(Consumer<byte[]> func) {
        this.hookedEvents.add(func);
    }

    private void pingRelay() throws Exception {

        HttpClient httpClient = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StealthPipe.config.RELAY_IP + "/ping"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("User-Agent", StealthPipe.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        LOGGER.info("Pinged relay");

        if (response.statusCode() != 200) {
            LOGGER.warn("Ping did not return status code 200");
        }
    }

    private void relayPingLoop() {
        new Thread(() -> {
            while (this.connected && this.isOpen()) {
                try {
                    this.pingRelay();
                } catch (Exception e) {
                    LOGGER.error("Failed to ping relay: ", e);
                }

                try {
                    Thread.sleep(DEFAULT_PING_INTERVAL);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private void sendLoop() {
        new Thread(() -> {
            while (this.connected && this.isOpen()) {
                long start = System.nanoTime();
                this.sendQueuedSendPackets();
                long elapsed = System.nanoTime() - start;
                long toWait = this.BATCHING_INTERVAL - elapsed;
                if (toWait > 0) {
                    if (toWait > 2_000_000L) { // >2ms -> park to save CPU
                        LockSupport.parkNanos(toWait - 500_000L); // park most of it
                    }
                    // short busy-spin to improve precision for the remaining nanos
                    while (System.nanoTime() - start < this.BATCHING_INTERVAL) {
                        Thread.onSpinWait();
                    }
                }
            }
        }).start();
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
                        ModState.outboundPPS.getAndAdd(1);
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

        if (!StealthPipe.config.ENABLE_BATCHED_PACKETS) {
            this.sendQueuedSendPackets();
        }

        /* Don`t do anything: long currentTime = System.nanoTime();
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


        } */

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




    @Override
    public void onOpen(ServerHandshake handshakeData) {
        // Logic for when the HTTP Upgrade succeeds

        LOGGER.info("WSS Connected successfully");

        LOGGER.info("Disabling Nagle's algorithm");
        setTcpNoDelay(true);

        LOGGER.info("WS Handshake success");
        this.connected = true;

        this.sendLoop();
        this.relayPingLoop();


        if (relayType == WebsocketClientType.CLIENT_TO_RELAY) {
            ModState.webSocketOpen.set(true);
        }

        if (relayType == WebsocketClientType.SERVER_TO_RELAY || relayType == WebsocketClientType.CLIENT_TO_RELAY) {
            for (byte[] packet : this.queuedPackets) {
                LOGGER.info("Sending queued packet");
                this.sendPacket(packet);
            }
            queuedPackets.clear();
        } else {
            for (byte[] packet : this.queuedPackets) {
                LOGGER.info("Sending queued packet");
                this.send(packet);
            }
            queuedPackets.clear();
        }




        this.keepAliveLoop();


    }

    private void keepAliveLoop() {
        if (this.relayType == WebsocketClientType.RELAY_SIGNALING) {


            byte[] data = new byte[] {(byte) SignalingMessageType.PING.getPacketType()};

            new Thread(() -> {

                LOGGER.info("Starting keep-alive");

                int numberOfErrorsDetected = 0;

                while (this.connected && this.isOpen()) {

                    try {
                        Thread.sleep(1000);
                        this.sentBegin.set(System.nanoTime());
                        this.send(data);

                        // check unstable and send message
                        if (System.currentTimeMillis() - lastAlertTime.get() > 15 * 60 * 1000) {
                            // alert if needed
                            if (this.RTTMonitor.isUnstable()) {


                                /* StealthPipe.CLIENT_PROXY.sendStealthPipeMessage(String.format("§cYour connection might be unstable and cause stuttering for other players. (average: %sms, standard deviation: %sms)",
                                        (int) this.RTTMonitor.getAverage(),
                                        (int) this.RTTMonitor.getStdDev()
                                        )); */
                                lastAlertTime.set(System.currentTimeMillis());
                            }
                        }

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

        this.gameChannel.get().pipeline().fireChannelRead(Unpooled.wrappedBuffer(data));




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
        clientChannel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(data));

        // LOGGER.info("Received {} bytes as client", data.length);



        // LOGGER.info("[CLIENT]: Received binary message, length: {}", data.length);
    }

    private Channel createVirtualChannel() {
        MinecraftServer server = ModState.minecraftServer.get();

        EmbeddedChannel virtualChannel = new EmbeddedChannel();
        ServerConnectionListener listener = server.getConnection();

        ((IConnectionInjector) listener).injectVirtualConnection(virtualChannel);

        return virtualChannel;
    }

    private void processRequestConnectionRequest(byte[] data) {
        // We need to create a fake channel

        /* don't do anything for now String newString = new String(data, StandardCharsets.UTF_8);

        Channel virtualChannel = this.createVirtualChannel();

        String url = String.format(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=%s&host=true&request=%s&version=%s", this.gameId, newString, StealthPipe.MOD_VERSION);

        StealthWebSocketClient newClient = new StealthWebSocketClient(URI.create(url), WebsocketClientType.SERVER_TO_RELAY, virtualChannel, gameId);
        newClient.connect();

        ModState.channelToWSClient.put(virtualChannel, newClient);

        LOGGER.info("Created new channel to relay"); */
    }

    private byte[] prepareSignalingMessage(byte clientId, byte messageType, byte[] message) {
        byte[] newArray = new byte[message.length + 2];
        newArray[0] = messageType;
        newArray[1] = clientId;
        System.arraycopy(message, 0, newArray, 2, message.length);

        return newArray;
    }

    private void handleRTCDisconnect(WebRTCGameConnection client) {

        /* // It is connection on server

        ModState.channelToRTCClient.entrySet().removeIf(entry -> {
            if (entry.getValue() == client) {
                LOGGER.info("Closed Netty channel on the server, and queued for removal");
                entry.getKey().disconnect(); // Close the Netty channel
                return true; // Removes this entry from the map
            }
            return false;
        });

        LOGGER.warn("Could not disconnect channel; WebRTC client not found"); */
    }

    private void processWebRTCRequestConnectionRequest(byte[] data) {
        /* LOGGER.info("Received a WebRTC Request Connection signal");

        byte clientId = data[1];

        if (!StealthPipe.config.HOST_ALLOW_WEBRTC_INBOUND) {
            // refuse request

            LOGGER.info("Refused WebRTC connection request, as stated in config");
            byte[] sendBackData = new byte[]{(byte) SignalingMessageType.WebRTC_ConnectionFailed.getPacketType(), clientId};

            this.send(sendBackData);
            return;
        }

        Channel virtualChannel = this.createVirtualChannel();

        WebRTCClient rtcClient = new WebRTCClient((byte[] message) -> {
            List<byte[]> packets = WebRTCClient.unpackPacket(message);
            for (byte[] packet : packets) {
                virtualChannel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(packet));
            }

        }, this::handleRTCDisconnect);

        byte[] readyData = new byte[]{(byte)SignalingMessageType.WebRTC_ConnectionReady.getPacketType(), clientId};
        this.send(readyData);
        LOGGER.info("Sent ready data");

        try {
            rtcClient.tryEstablishRTCHost(this, clientId);
            // success
            LOGGER.info("RTC Client established!");
            ModState.channelToRTCClient.put(virtualChannel, rtcClient);
        } catch (Exception e) {
            LOGGER.error("RTC connection failed to establish");
            this.send(this.prepareSignalingMessage(clientId, (byte)SignalingMessageType.WebRTC_ConnectionFailed.getPacketType(), new byte[0]));
        } */



    }

    private void processConnectionRequest(byte[] data) {
        String newString = new String(data, StandardCharsets.UTF_8);

        if (newString.startsWith("REQUESTCONNECTION_")) {
            processRequestConnectionRequest(data);
        } else {
            byte messageType = data[0];
            if (messageType == (byte)SignalingMessageType.WebRTC_RequestConnection.getPacketType()) {
                this.processWebRTCRequestConnectionRequest(data);
            } else {
                LOGGER.warn("Unknown message type: {}", messageType);
            }
        }


    }

    private void processMessageSignaling(byte[] data) {
        String newString = new String(data, StandardCharsets.UTF_8);

         if (data.length >= 1 && data[0] == SignalingMessageType.PONG.getPacketType()) {
            this.sentEnd.set(System.nanoTime());

            // calculate time taken
            long timeElapsed = this.sentEnd.get() - this.sentBegin.get();
            int millisecondsElapsed = Math.toIntExact(TimeUnit.NANOSECONDS.toMillis(timeElapsed));

            this.pingMilliseconds.set(millisecondsElapsed);

            // LOGGER.info("Ping to relay is: {}", millisecondsElapsed);
            ModState.ping.set(millisecondsElapsed);
            this.RTTMonitor.addSample((double) millisecondsElapsed);
        } else {
             processConnectionRequest(data);
         }
    }

    public void disconnectWithReason(ConnectionDisconnectReason reason) {
        LOGGER.info("Close called with reason: {}", reason.getPacketType());
        this.close(1000, reason.getPacketType());
    }

    @Override
    public void onMessage(ByteBuffer byteBuf) {
        // Logic for when the server sends data to your mod


        this.gotMessages = true;

        byte[] data = new byte[byteBuf.remaining()];
        byteBuf.get(data);

        for (Consumer<byte[]> event : this.hookedEvents) {
            LOGGER.info("sending data to consumer");
            event.accept(data);
        }

        ModState.inboundData.getAndAdd(data.length);
        ModState.inboundBandwidth.getAndAdd(data.length);
        ModState.inboundPPS.getAndAdd(1);


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
            // RELAY_TO_CLIENT or CLIENT_TO_RELAY
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

        LOGGER.info("WSS client disconnected for reason: {}", reason);

        if (Objects.equals(reason, "") && code == 1006) {
            reason = "ABNORMAL_DISCONNECT";
        }

        boolean isClient = ModState.isClientConnectingToStealthServer.get();

        if (isClient) {

            if (this.relayType == WebsocketClientType.CLIENT_SIGNALING) {
                LOGGER.info("not warning disconnect for client signal disconnect");
                return;
            }
            // Disconnect the channel


            DisconnectHandler.showClientDisconnectMessage(this.gotMessages, reason);



            this.gameChannel.ifPresent(Channel::disconnect);


            LOGGER.info("WS Disconnected client channel");


        } else {

            if (this.relayType == WebsocketClientType.RELAY_SIGNALING) {
                // disconnect WebRTC connections
                /* for (Map.Entry<Channel, WebRTCClient> clients : ModState.channelToRTCClient.entrySet()) {
                    LOGGER.info("Disconnecting RTC Client. Cause: signal disconnected. Preserve state.");
                    clients.getValue().disconnect();
                }

                LOGGER.info("RELAY SIGNAL disconnected");
                DisconnectHandler.showDisconnectMessageAndRetry(reason);
                return; */
            }

            // It is connection on server

            /* ModState.channelToWSClient.entrySet().removeIf(entry -> {
                if (entry.getValue() == this) {
                    LOGGER.info("Closed Netty channel on the server, and queued for removal");
                    entry.getKey().disconnect(); // Close the Netty channel
                    return true; // Removes this entry from the map
                }
                return false;
            });

            LOGGER.warn("Could not disconnect channel; WS client not found");
            */

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
        ModState.outboundBandwidth.getAndAdd(data.length);



        if (this.connected) {
            /* this is some code: if (ModState.isClientConnectingToStealthServer.get()) {
                LOGGER.info("Sending {} bytes", data.length);
            } */

            if (!this.isOpen()) {
                LOGGER.warn("Attempted to send binary data when the websocket is not open or has already closed");
                return;
            }

            if (this.relayType == WebsocketClientType.RELAY_SIGNALING || this.relayType == WebsocketClientType.CLIENT_SIGNALING) {
                // Don't batch signaling data
                ModState.outboundPPS.getAndAdd(1);
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