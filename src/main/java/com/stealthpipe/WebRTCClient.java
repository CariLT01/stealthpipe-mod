package com.stealthpipe;

import com.google.gson.Gson;
import dev.onvoid.webrtc.*;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WebRTCClient {

    private final Gson gson = new Gson();
    private final PeerConnectionFactory factory = new PeerConnectionFactory();
    private RTCPeerConnection peerConnection;
    private StealthWebSocketClient signalingClient;
    private RTCDataChannel dataChannel;

    private final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private byte clientId;

    private boolean isHost = false;

    private final List<byte[]> queuedPackets = new ArrayList<>();
    private boolean open = false;

    private Consumer<byte[]> onMessageHook;

    private final CompletableFuture<Void> connectionFuture = new CompletableFuture<>();

    public WebRTCClient(Consumer<byte[]> onMessage) {
        this.clientId = (byte) (Math.random() * 255);
        this.onMessageHook = onMessage;
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
                    open = true;
                    ModState.webSocketOpen.set(true);
                    connectionFuture.complete(null);
                    for (byte[] packet : queuedPackets) {
                        try { onSendPacketInternal(packet); } catch (Exception e) { LOGGER.error("Queue flush failed", e); }
                    }
                    queuedPackets.clear();
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
        Map<String, Object> signal = gson.fromJson(message, Map.class);
        String type = (String) signal.get("type");

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
    }

    private void handleSignalMessage(byte[] message) {
        if (message.length < 2) {
            LOGGER.warn("ignore message length < 2");
            return;
        }
        byte messageType = message[0];
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
            }
        });

        this.createRTC(true);

        byte[] message = {(byte) SignalingMessageType.WebRTC_RequestConnection.getPacketType(), clientId};
        this.hookMessage();
        signalingClient.send(message);

        try {
            connectionFuture.get(30, TimeUnit.SECONDS);
            LOGGER.info("WebRTC Connection Success");
        } catch (Exception e) {
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

            System.out.println("[StealthPipe V6] P2P Disconnected. Native resources freed.");

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
}
