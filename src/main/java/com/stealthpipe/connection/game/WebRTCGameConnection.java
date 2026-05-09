package com.stealthpipe.connection.game;

import com.google.gson.Gson;
import com.stealthpipe.ModState;
import com.stealthpipe.connection.PacketBatchingManager;
import com.stealthpipe.StealthPipe;
import com.stealthpipe.connection.AbstractStealthPipeWebSocketClient;
import com.stealthpipe.enums.SignalConnectionFlow;
import com.stealthpipe.connection.signal.SignalWebSocket;
import com.stealthpipe.enums.SignalingMessageType;
import com.stealthpipe.enums.ConnectionDisconnectReason;
import com.stealthpipe.enums.PacketFlow;
import dev.onvoid.webrtc.*;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class WebRTCGameConnection implements GameConnectionInterface {

    private final Gson gson = new Gson();
    private final PeerConnectionFactory factory = new PeerConnectionFactory();
    private RTCPeerConnection peerConnection;
    private AbstractStealthPipeWebSocketClient signalingClient;
    private RTCDataChannel dataChannel;

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private byte clientId;

    private final AtomicBoolean loopStarted = new AtomicBoolean(false);


    private boolean isHost = false;

    private final ConcurrentLinkedQueue<byte[]> queuedPackets = new ConcurrentLinkedQueue<>();
    private boolean open = false;

    private final Consumer<byte[]> onMessageHook;
    private final Consumer<WebRTCGameConnection> onClosed;

    private final AtomicBoolean connectionFailed = new AtomicBoolean(false);

    private final CompletableFuture<Void> connectionFuture = new CompletableFuture<>();

    public boolean gotMessages = false;
    private final ReentrantLock writeLock = new ReentrantLock();

    private final PacketBatchingManager packetBatchingManager = new PacketBatchingManager(this::send);

    private SignalWebSocket aSignalingClient = null;
    private byte aOtherClientID = 0;
    private String aGameID = null;
    private final PacketFlow flow;

    /* Debugging statistics */
    private final AtomicInteger iceCandidatesReceivedCount = new AtomicInteger(0);
    private final AtomicInteger iceCandidatesSentCount = new AtomicInteger(0);
    private final AtomicBoolean connectionDone = new AtomicBoolean(false); // Responsible to prevent lingering status text



    public WebRTCGameConnection(Consumer<byte[]> onMessage, Consumer<WebRTCGameConnection> onClosed, PacketFlow flow, String gameId) {
        if (flow != PacketFlow.ClientToHost) {
            throw new IllegalArgumentException("Cannot use client to host constructor, invalid flow");
        }

        this.flow = flow;

        this.clientId = (byte) (Math.random() * 255);
        this.onMessageHook = onMessage;
        this.onClosed = onClosed;
        this.aGameID = gameId;
        this.iceCandidatesSentCount.set(0);
        this.iceCandidatesReceivedCount.set(0);
    }

    public WebRTCGameConnection(Consumer<byte[]> onMessage, Consumer<WebRTCGameConnection> onClosed, PacketFlow flow, SignalWebSocket signalingClient, byte otherClientID) {
        if (flow != PacketFlow.HostToClient) {
            throw new IllegalArgumentException("Cannot use host to client constructor, invalid flow");
        }

        this.flow = flow;

        this.onMessageHook = onMessage;
        this.onClosed = onClosed;
        this.aSignalingClient = signalingClient;
        this.aOtherClientID = otherClientID;
        this.iceCandidatesSentCount.set(0);
        this.iceCandidatesReceivedCount.set(0);

    }

    private void reportConnectionStatus(int index, Component text) {
        if (flow != PacketFlow.ClientToHost) {
            // Don't show any messages on the host-side when connecting via WebRTC
            return;
        }
        if (this.connectionDone.get()) {
            // Connection already established; don't add more bloat
            return;
        }
        StealthPipe.CLIENT_PROXY.setConnectionStatusIndex(text, index);
    }

    private void clearConnectionStatus() {
        if (flow != PacketFlow.ClientToHost) {
            // Don't manipulate on the host side
            return;
        }
        StealthPipe.CLIENT_PROXY.resizeConnectionStatusList(0);
        this.connectionDone.set(true);
    }

    private void updateIceCandidateCount() {
        reportConnectionStatus(1, Component.translatable("status.stealthpipe.iceNegotiation", iceCandidatesReceivedCount.get(), iceCandidatesSentCount.get()));
    }

    public PacketBatchingManager getPacketBatchingManager() {
        return packetBatchingManager;
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
                    clearConnectionStatus();
                    if (connectionFailed.get()) {
                        // already failed, close the connection
                        disconnectWebRTC();
                        return;
                    }
                    open = true;
                    clearConnectionStatus();
                    ModState.webSocketOpen.set(true);
                    connectionFuture.complete(null);
                    byte[] packet;
                    // poll() retrieves AND removes the head of the queue in one atomic step
                    while ((packet = queuedPackets.poll()) != null) {
                        LOGGER.debug("Sent {} queued bytes", packet.length);
                        try { onSendPacketInternal(packet); } catch (Exception e) { LOGGER.error("Queue flush failed", e); }
                    }
                    queuedPackets.clear();
                    if (!loopStarted.get()) {
                        loopStarted.set(true);
                        packetBatchingManager.run();
                    }

                }

                if (channel.getState() == RTCDataChannelState.CLOSED || channel.getState() == RTCDataChannelState.CLOSING) {
                    open = false;
                    onClosedInternal();
                    onClosed.accept(WebRTCGameConnection.this);
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
                                        reportConnectionStatus(1, Component.translatable("status.stealthpipe.reportingWrtcAnswer"));
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
                iceCandidatesReceivedCount.getAndAdd(1);
                updateIceCandidateCount();
                if (StealthPipe.config.LOG_WRTC_ICE_CANDIDATES) {


                    LOGGER.info("[DEBUG SENSITIVE INFO] Got ICE candidate: {}", candidate);
                } else {
                    LOGGER.info("Got ICE candidate: [hidden]");
                }
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

        if (StealthPipe.config.LOG_WRTC_ICE_CANDIDATES) {
            LOGGER.info("[DEBUG SENSITIVE INFO] WebRTC received signaling message: {}", messageStr);
        } else {
            LOGGER.info("WebRTC received signaling message: [hidden]");
        }


        this.handleSignalMessageStr(messageStr);

    }

    public void connect() throws Exception {
        try {
            reportConnectionStatus(0, Component.translatable("status.stealthpipe.wrtcConnecting"));
            if (this.flow == PacketFlow.ClientToHost) {
                this.clientTryEstablishRTC(this.aGameID);
            } else {
                this.hostTryEstablishRTC(this.aSignalingClient, this.aOtherClientID);
            }
        } catch (Throwable t) {
            clearConnectionStatus();
            throw t;
        }

    }


    private void hostTryEstablishRTC(AbstractStealthPipeWebSocketClient signalingClient, byte otherClientID) throws Exception {
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
        stunServer.urls.add("stun:stun1.l.google.com:19302");
        stunServer.urls.add("stun:stun2.l.google.com:19302");
        stunServer.urls.add("stun:stun3.l.google.com:19302");
        stunServer.urls.add("stun:stun4.l.google.com:19302");
        stunServer.urls.add("stun:stun.stunprotocol.org:3478");
        stunServer.urls.add("stun:stun.voiparound.com");
        stunServer.urls.add("stun:stun.voipbuster.com");
        stunServer.urls.add("stun:stun.voipstunt.com");
        stunServer.urls.add("stun:stun.counterpath.net");
        stunServer.urls.add("stun:stun.ekiga.net");
        stunServer.urls.add("stun:stun.ideasip.com");
        stunServer.urls.add("stun:stun.schlund.de");
        stunServer.urls.add("stun:stun.rixtelecom.se");
        stunServer.urls.add("stun:stun.sipgate.net");
        stunServer.urls.add("stun:stun.sipphone.com");
        stunServer.urls.add("stun:stun.t-online.de");
        stunServer.urls.add("stun:stun.iptel.org");
        stunServer.urls.add("stun:stun.1und1.de");
        stunServer.urls.add("stun:stun.sipnet.net");

        config.iceServers.add(stunServer);
        config.iceTransportPolicy = RTCIceTransportPolicy.ALL;




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
                iceCandidatesSentCount.getAndAdd(1);
                updateIceCandidateCount();
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

    private void clientTryEstablishRTC(String gameId) throws Exception {
        this.isHost = false;
        /* signalingClient = new StealthWebSocketClient(URI.create(
                StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=" + gameId + "&signal=true&version=" + StealthPipe.MOD_VERSION),
                WebsocketClientType.CLIENT_SIGNALING, gameId); */

        signalingClient = new SignalWebSocket(gameId, SignalConnectionFlow.ClientToRelay);

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
            signalingClient.disconnectWithReason(ConnectionDisconnectReason.SignalingWebRTCFailed);
            throw new RuntimeException("Timed out waiting for READY status or WRTC rejected");
        }


        try {
            connectionFuture.get(15, TimeUnit.SECONDS);
            LOGGER.info("WebRTC Connection Success");
            this.connectionFailed.set(false);
            LOGGER.info("Disconnecting signaling WebSocket");
            signalingClient.disconnectWithReason(ConnectionDisconnectReason.SignalingFinished);
        } catch (Exception e) {
            this.connectionFailed.set(true);
            peerConnection.close();
            signalingClient.disconnectWithReason(ConnectionDisconnectReason.SignalingWebRTCFailed);
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

        if (StealthPipe.config.LOG_WRTC_ICE_CANDIDATES) {
            LOGGER.info("[DEBUG SENSITIVE INFO] WebRTC client -> send msg: {}", msg);
        } else {
            LOGGER.info("WebRTC client send msg: [hidden]");
        }


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

    private void onClosedInternal() {
        this.packetBatchingManager.stop();
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
                // peerConnection.(); .dispose() doesn't exist here
            }

            LOGGER.info("Disconnected WebRTC P2P");
            this.open = false;
            // onClosed.accept(this);

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
    public void disconnectWithReason(ConnectionDisconnectReason reason) {this.disconnectWebRTC();} // WebRTC does not support custom disconnect frames

    public void send(byte[] data) {
        try {
            if (!this.open) {
                this.queuedPackets.add(data);
            } else {
                this.onSendPacketInternal(data);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send data via WebRTC", e);
        }
    }

    public void sendPacket(byte[] data) {
        this.packetBatchingManager.queuePacket(data);
    }
}
