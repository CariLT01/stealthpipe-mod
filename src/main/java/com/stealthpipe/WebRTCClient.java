package com.stealthpipe;

import com.google.gson.Gson;
import dev.onvoid.webrtc.*;
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
import java.util.function.Consumer;

public class WebRTCClient {

    private final Gson gson = new Gson();
    private PeerConnectionFactory factory;
    private RTCPeerConnection peerConnection;
    private StealthWebSocketClient signalingClient;
    private RTCDataChannel dataChannel;

    private final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private final byte clientId;

    private final List<byte[]> queuedPackets = new ArrayList<>();
    private boolean open = false;

    private Consumer<byte[]> onMessageHook;

    private final CompletableFuture<Void> offerFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> answerFuture = new CompletableFuture<>();

    public WebRTCClient(Consumer<byte[]> onMessage) {
        this.clientId = (byte) (Math.random() * 255);
        this.onMessageHook = onMessage;
    }

    private void handleSignalMessageStr(String message) {
        Map<String, Object> signal = gson.fromJson(message, Map.class);
        String type = (String) signal.get("type");

        if ("answer".equals(type)) {
            String sdp = (String) ((Map) signal.get("data")).get("sdp");
            peerConnection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, sdp), new SetSessionDescriptionObserver() {
                @Override public void onSuccess() {
                    answerFuture.complete(null);
                    LOGGER.info("WebRTC success");
                }

                @Override
                public void onFailure(String s) {
                    LOGGER.error("WebRTC connection failed: {}", s);
                    answerFuture.completeExceptionally(new RuntimeException("Failed to set remote description: "+ s));
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
        byte messageType = message[0];
        byte[] messageContents = Arrays.copyOfRange(message, 1, message.length);
        String messageStr = new String(messageContents, StandardCharsets.UTF_8);
        LOGGER.info("WebRTC received signaling message: {}", messageStr);

        this.handleSignalMessageStr(messageStr);

    }

    public void tryEstablishRTC(String gameId) throws Exception {
        signalingClient = new StealthWebSocketClient(URI.create(
                StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=" + gameId + "&signal=true&version=" + StealthPipe.MOD_VERSION),
                WebsocketClientType.CLIENT_SIGNALING, gameId);

        signalingClient.connect();

        signalingClient.hookOnMessage(this::handleSignalMessage);

        RTCConfiguration config = new RTCConfiguration();
        RTCIceServer stunServer = new RTCIceServer();
        stunServer.urls.add("stun:stun.l.google.com:19302");
        config.iceServers.add(stunServer);

        PeerConnectionFactory factory = new PeerConnectionFactory();

        byte clientId = (byte)(Math.random() * 255);

        peerConnection = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                sendToSignaling("candidate", Map.of(
                        "sdp", candidate.sdp,
                        "sdpMid", candidate.sdpMid,
                        "sdpMLineIndex", candidate.sdpMLineIndex
                ));

            }
        });

        startCall();
        offerFuture.join();
        answerFuture.join();
        setupDataChannel();

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
                        offerFuture.complete(null);
                    }

                    @Override
                    public void onFailure(String s) {
                        LOGGER.error("WebRTC set failed: {}", s);
                        offerFuture.completeExceptionally(new RuntimeException(String.format("Failed to set local description: %s", s)));
                    }
                });
            }

            @Override
            public void onFailure(String s) {
                LOGGER.error("WebRTC create failed: {}", s);
                offerFuture.completeExceptionally(new RuntimeException("Failed to create offer: "+ s));
            }
        });
    }

    private void onMessageRTC(byte[] data) {
        LOGGER.info("WebRTC pipe received {} bytes of data", data.length);
    }

    private void setupDataChannel() {
        RTCDataChannelInit init = new RTCDataChannelInit();
        dataChannel = peerConnection.createDataChannel("main", init);

        dataChannel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onBufferedAmountChange(long l) {
                LOGGER.info("WebRTC buffered amount changed: {}", l);
            }

            @Override
            public void onStateChange() {
                LOGGER.info("WebRTC connection state changed: {}", dataChannel.getState());

                if (dataChannel.getState() == RTCDataChannelState.OPEN) {
                    open = true;

                    for (byte[] packet : queuedPackets) {
                        try {
                            onSendPacketInternal(packet);
                        } catch (Exception e) {
                            LOGGER.error("webrtc emit packet queued failed:", e);
                        }

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
    private void onSendPacketInternal(byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        dataChannel.send(new RTCDataChannelBuffer(buffer, true));
    }

    public void send(byte[] data) throws Exception {
        if (!this.open) {
            this.queuedPackets.add(data);
        } else {
            this.onSendPacketInternal(data);
        }
    }
}
