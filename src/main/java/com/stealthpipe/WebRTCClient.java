package com.stealthpipe;

import com.google.gson.Gson;
import dev.onvoid.webrtc.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class WebRTCClient {

    private final Gson gson = new Gson();
    private final PeerConnectionFactory factory = new PeerConnectionFactory();
    private RTCPeerConnection peerConnection;
    private StealthWebSocketClient signalingClient;
    private RTCDataChannel dataChannel;

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private byte clientId;

    private final long BATCHING_INTERVAL = StealthPipe.config.PACKET_BATCHING_INTERVAL_MS * 1_000_000L; // 2 milliseconds

    private boolean isHost = false;

    private final List<byte[]> queuedPackets = new ArrayList<>();
    private boolean open = false;

    private final Consumer<byte[]> onMessageHook;
    private final Consumer<WebRTCClient> onClosed;
    private final AtomicBoolean loopStarted = new AtomicBoolean(false);

    private final AtomicBoolean connectionFailed = new AtomicBoolean(false);

    private final CompletableFuture<Void> connectionFuture = new CompletableFuture<>();

    public boolean gotMessages = false;
    private final ReentrantLock writeLock = new ReentrantLock();

    private final Queue<byte[]> queuedSendPackets = new ConcurrentLinkedQueue<>();

    public WebRTCClient(Consumer<byte[]> onMessage, Consumer<WebRTCClient> onClosed) {
        this.clientId = (byte) (Math.random() * 255);
        this.onMessageHook = onMessage;
        this.onClosed = onClosed;
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
                        this.send(flatBatch);
                    } catch (Exception e) {
                        LOGGER.error("Failed to send", e);
                    }
                    finally {
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

    public static List<byte[]> unpackPacket(byte[] packedData) {
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
                        this.send(flat);
                    } catch (Exception e) {
                        LOGGER.error("Failed to send packet", e);
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

    private void sendLoop() {
        new Thread(() -> {
            while (this.open) {
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

    private void registerDataChannelObserver(RTCDataChannel channel) {
        this.dataChannel = channel;
        channel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long l) {}

            @Override
            public void onStateChange() {
                LOGGER.info("WebRTC DataChannel State: {}", channel.getState());
                if (channel.getState() == RTCDataChannelState.OPEN) {
                    if (connectionFailed.get()) {
                        // already failed, close the connection
                        disconnectWebRTC();
                        return;
                    }
                    open = true;
                    ModState.webSocketOpen.set(true);
                    connectionFuture.complete(null);
                    for (byte[] packet : queuedPackets) {
                        try { onSendPacketInternal(packet); } catch (Exception e) { LOGGER.error("Queue flush failed", e); }
                    }
                    queuedPackets.clear();
                    if (!loopStarted.get()) {
                        loopStarted.set(true);
                        sendLoop();
                    }

                }

                if (channel.getState() == RTCDataChannelState.CLOSED || channel.getState() == RTCDataChannelState.CLOSING) {
                    open = false;
                    onClosed.accept(WebRTCClient.this);
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                byte[] data = new byte[buffer.data.remaining()];
                buffer.data.get(data);

                onMessageRTC(data);
            }
        });
    }

    private void handleSignalMessageStr(String message) {
        try {
            Map<String, Object> signal = gson.fromJson(message, Map.class);
            if (signal == null) {
                LOGGER.warn("got invalid JSON: {}", message);
                return;
            }

            String type = (String) signal.get("type");

            if (peerConnection == null) {
                LOGGER.warn("dropped signal packet. peer connection not initialized");
                return;
            }



            /* giant nesting */
            if ("offer".equals(type)) {
                String sdp = (String) ((Map) signal.get("data")).get("sdp");
                peerConnection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, sdp), new SetSessionDescriptionObserver() {
                    @Override public void onSuccess() {
                        RTCAnswerOptions options = new RTCAnswerOptions();
                        peerConnection.createAnswer(options, new CreateSessionDescriptionObserver() {
                            @Override public void onSuccess(RTCSessionDescription description) {
                                peerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {
                                    @Override public void onSuccess() {
                                        sendToSignaling("answer", Map.of("sdp", description.sdp));
                                    }
                                    @Override public void onFailure(String s) { connectionFuture.completeExceptionally(new RuntimeException(s)); }
                                });
                            }
                            @Override public void onFailure(String s) { connectionFuture.completeExceptionally(new RuntimeException(s)); }
                        });
                    }
                    @Override public void onFailure(String s) { connectionFuture.completeExceptionally(new RuntimeException(s)); }
                });
            }
            else if ("answer".equals(type)) {
                String sdp = (String) ((Map) signal.get("data")).get("sdp");
                peerConnection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, sdp), new SetSessionDescriptionObserver() {
                    @Override public void onSuccess() {
                        LOGGER.info("WebRTC success");
                    }

                    @Override
                    public void onFailure(String s) {
                        LOGGER.error("WebRTC connection failed: {}", s);
                        connectionFuture.completeExceptionally(new RuntimeException("Failed to set remote description: "+ s));
                    }
                });
            } else if ("candidate".equals(type)) {
                Map data = (Map) signal.get("data");
                RTCIceCandidate candidate = new RTCIceCandidate(
                        (String) data.get("sdpMid"),
                        ((Double) data.get("sdpMLineIndex")).intValue(),
                        (String) data.get("sdp")
                );
                peerConnection.addIceCandidate(candidate);
                LOGGER.info("Got ICE candidate: {}", candidate);
            }
        } catch (Exception e) {
            LOGGER.error("WebRTC handle signal message failed", e);
        }

    }

    private void handleSignalMessage(byte[] message) {
        if (message.length == 0) {
            LOGGER.warn("Ignore empty signal packet");
            return;
        }
        byte messageType = message[0];
        if (messageType == SignalingMessageType.PONG.getPacketType()) {
            return;
        }
        if (messageType == SignalingMessageType.PING.getPacketType()) {
            return;
        }
        if (message.length < 2) {
            LOGGER.warn("ignore message length < 2");
            return;
        }

        if (messageType == SignalingMessageType.WebRTC_ConnectionFailed.getPacketType()) {
            LOGGER.warn("Other recipient refused WebRTC connection");
            connectionFuture.completeExceptionally(new RuntimeException("host refused WebRTC connection"));
            return;
        }

        byte[] messageContents = Arrays.copyOfRange(message, 2, message.length);
        String messageStr = new String(messageContents, StandardCharsets.UTF_8);
        if (messageStr.startsWith("REQUESTCONNECTION")) {
            LOGGER.warn("ignore request connection message");
            return;
        }

        LOGGER.info("WebRTC received signaling message: {}", messageStr);

        this.handleSignalMessageStr(messageStr);

    }


    public void tryEstablishRTCHost(StealthWebSocketClient signalingClient, byte otherClientID) throws Exception {
        LOGGER.info("Trying to establish RTC connection as a host");

        this.isHost = true;
        this.clientId = otherClientID;
        this.signalingClient = signalingClient;
        this.hookMessage();
        this.createRTC(false);
    }

    private void createRTC(boolean isOfferer) {

        LOGGER.info("Creating RTC connection...");



        RTCConfiguration config = new RTCConfiguration();
        RTCIceServer stunServer = new RTCIceServer();
        stunServer.urls.add("stun:stun.l.google.com:19302");
        config.iceServers.add(stunServer);



        peerConnection = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                if (StealthPipe.config.SIMULATE_ICE_CANDIDATES_FAILURE) {
                    LOGGER.warn("[debug] Not sending ICE candidate. Simulating ICE candidates failure");
                    return;
                }
                if (connectionFailed.get()) {
                    return;
                }
                sendToSignaling("candidate", Map.of(
                        "sdp", candidate.sdp,
                        "sdpMid", candidate.sdpMid,
                        "sdpMLineIndex", candidate.sdpMLineIndex
                ));

            }
            @Override
            public void onDataChannel(RTCDataChannel remoteChannel) {
                LOGGER.info("Host received remote DataChannel: {}", remoteChannel.getLabel());
                registerDataChannelObserver(remoteChannel);
            }
        });

        if (isOfferer) {
            RTCDataChannelInit init = new RTCDataChannelInit();
            init.ordered = true;
            init.negotiated = false;
            RTCDataChannel localChannel = peerConnection.createDataChannel("main", init);
            registerDataChannelObserver(localChannel);
            startCall();
        }
    }

    private void hookMessage() {
        signalingClient.hookOnMessage(this::handleSignalMessage);
    }

    public void tryEstablishRTC(String gameId) throws Exception {
        this.isHost = false;
        signalingClient = new StealthWebSocketClient(URI.create(
                StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=" + gameId + "&signal=true&version=" + StealthPipe.MOD_VERSION),
                WebsocketClientType.CLIENT_SIGNALING, gameId);

        signalingClient.connect();
        CompletableFuture<Void> readyFuture = new CompletableFuture<>();
        signalingClient.hookOnMessage((byte[] data) -> {
            if (data[0] == SignalingMessageType.WebRTC_ConnectionReady.getPacketType()) {
                LOGGER.info("Other side reported RTC negotiation start ready status");
                readyFuture.complete(null);
            } else if (data[0] == SignalingMessageType.WebRTC_ConnectionFailed.getPacketType()) {
                LOGGER.error("Other side rejected WebRTC connection");
                readyFuture.completeExceptionally(new RuntimeException("Host rejected WebRTC"));
            }
        });

        this.createRTC(true);

        byte[] message = {(byte) SignalingMessageType.WebRTC_RequestConnection.getPacketType(), clientId};
        this.hookMessage();
        signalingClient.send(message);
        try {
            readyFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("ReadyFuture failed: ", e);
            this.connectionFailed.set(true);
            throw new RuntimeException("Host did not report status READY, timed out waiting for READY status");
        }


        try {
            connectionFuture.get(15, TimeUnit.SECONDS);
            LOGGER.info("WebRTC Connection Success");
            this.connectionFailed.set(false);
            LOGGER.info("Disconnecting signaling WebSocket");
            signalingClient.disconnectWithReason(WebSocketDisconnectReason.SignalingFinished);
        } catch (Exception e) {
            this.connectionFailed.set(true);
            peerConnection.close();
            signalingClient.disconnectWithReason(WebSocketDisconnectReason.SignalingWebRTCFailed);
            throw new RuntimeException("WebRTC connection failed or timed out");
        }

    }

    private byte[] prepareSignalingMessage(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] newArray = new byte[bytes.length + 2];
        newArray[0] = (byte) SignalingMessageType.WebRTC_HandshakeMessage.getPacketType();
        newArray[1] = clientId;
        System.arraycopy(bytes, 0, newArray, 2, bytes.length);

        return newArray;
    }

    private void sendToSignaling(String type, Object data) {



        String msg = gson.toJson(Map.of("type", type, "data", data));
        LOGGER.info("WebRTC client -> send msg: {}", msg);
        signalingClient.send(this.prepareSignalingMessage(msg));
    }

    private void startCall() {
        // Create an Offer to send to the other peer
        peerConnection.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                peerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {
                        // Send the SDP Offer to the Go server
                        sendToSignaling("offer", Map.of("sdp", description.sdp));
                    }

                    @Override
                    public void onFailure(String s) {
                        LOGGER.error("WebRTC set failed: {}", s);
                        connectionFuture.completeExceptionally(new RuntimeException(String.format("Failed to set local description: %s", s)));
                    }
                });
            }

            @Override
            public void onFailure(String s) {
                LOGGER.error("WebRTC create failed: {}", s);
                connectionFuture.completeExceptionally(new RuntimeException("Failed to create offer: "+ s));
            }
        });
    }

    private void onMessageRTC(byte[] data) {
        // LOGGER.info("WebRTC pipe received {} bytes of data", data.length);
        this.gotMessages = true;
        ModState.inboundPPSCounter.getAndAdd(1);
        this.onMessageHook.accept(data);

    }


    private void onSendPacketInternal(byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        dataChannel.send(new RTCDataChannelBuffer(buffer, true));
    }

    private void disconnectWebRTC() {
        try {
            // 1. Close the Data Channel first
            if (dataChannel != null) {
                dataChannel.unregisterObserver(); // Stop listening to 170k PPS
                dataChannel.close();
                dataChannel.dispose(); // Crucial in Java to free native memory
            }

            // 2. Close the Peer Connection
            if (peerConnection != null) {
                peerConnection.close();
                // peerConnection.(); // Cleans up the C++ backend
            }

            LOGGER.info("Disconnected WebRTC P2P");
            this.open = false;
            onClosed.accept(this);

        } catch (Exception e) {
            // Log it, but don't let it crash the WSS fallback
            System.err.println("Error during P2P cleanup: " + e.getMessage());
        } finally {
            this.dataChannel = null;
            this.peerConnection = null;
        }
    }

    public void disconnect() {
        this.disconnectWebRTC();
    }

    public void send(byte[] data) throws Exception {
        if (!this.open) {
            this.queuedPackets.add(data);
        } else {
            this.onSendPacketInternal(data);
        }
    }

    private void checkShouldFire() {

        if (!StealthPipe.config.ENABLE_BATCHED_PACKETS) {
            this.sendQueuedSendPackets();
        }
    }

    public void sendPacket(byte[] data) {
        this.queuedSendPackets.add(data);

        this.checkShouldFire();
    }
}
