package com.stealthpipe;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SignalWebSocket extends AbstractStealthPipeWebSocketClient {

    private final SignalConnectionFlow flow;

    private final int DEFAULT_PING_INTERVAL = 300 * 1000;

    private final String gameId;

    private final RoundTripTimeMonitor RTTMonitor = new RoundTripTimeMonitor(
            10,
            120,
            50
    );

    private final AtomicLong sentBegin = new AtomicLong(System.nanoTime());
    private final AtomicLong sentEnd = new AtomicLong(System.nanoTime());

    public SignalWebSocket(String gameId, SignalConnectionFlow flow) {
        super(gameId, flow == SignalConnectionFlow.HostToRelay, Optional.empty(), flow == SignalConnectionFlow.ClientToRelay);
        this.flow = flow;
        this.gameId = gameId;
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

    private void keepAliveLoop() {
        byte[] data = new byte[] {(byte) SignalingMessageType.PING.getPacketType()};

        new Thread(() -> {

            LOGGER.info("Starting keep-alive");

            int numberOfErrorsDetected = 0;

            while (this.connected && this.isOpen()) {

                try {
                    Thread.sleep(1000);
                    this.send(data);

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
    }

    private byte[] prepareSignalingMessage(byte clientId, byte messageType, byte[] message) {
        byte[] newArray = new byte[message.length + 2];
        newArray[0] = messageType;
        newArray[1] = clientId;
        System.arraycopy(message, 0, newArray, 2, message.length);

        return newArray;
    }

    private void handleRTCDisconnect(WebRTCClient client) {

        // It is connection on server

        ModState.channelToRTCClient.entrySet().removeIf(entry -> {
            if (entry.getValue() == client) {
                LOGGER.info("Closed Netty channel on the server, and queued for removal");
                entry.getKey().disconnect(); // Close the Netty channel
                return true; // Removes this entry from the map
            }
            return false;
        });

        LOGGER.warn("Could not disconnect channel; WebRTC client not found");
    }

    private Channel createVirtualChannel() {
        MinecraftServer server = ModState.minecraftServer.get();

        EmbeddedChannel virtualChannel = new EmbeddedChannel();
        ServerConnectionListener listener = server.getConnection();

        ((IConnectionInjector) listener).injectVirtualConnection(virtualChannel);

        return virtualChannel;
    }

    private void processWebRTCRequestConnectionRequest(byte[] data) {
        LOGGER.info("Received a WebRTC Request Connection signal");

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
            List<byte[]> packets = PacketBatchingManager.unpackPacket(message);
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
        }



    }

    private void processRequestConnectionRequest(byte[] data) {
        // We need to create a fake channel

        String newString = new String(data, StandardCharsets.UTF_8);

        Channel virtualChannel = this.createVirtualChannel();

        String url = String.format(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=%s&host=true&request=%s&version=%s", this.gameId, newString, StealthPipe.MOD_VERSION);

        // StealthWebSocketClient newClient = new StealthWebSocketClient(URI.create(url), WebsocketClientType.SERVER_TO_RELAY, virtualChannel, gameId);
        // newClient.connect();

        GameConnectionWebSocket newClient = new GameConnectionWebSocket(gameId, PacketFlow.HostToClient, virtualChannel, newString);
        newClient.connect();

        ModState.channelToWSClient.put(virtualChannel, newClient);

        LOGGER.info("Created new channel to relay");
    }

    private void processConnectionRequest(byte[] data) {
        String newString = new String(data, StandardCharsets.UTF_8);

        if (newString.startsWith("REQUESTCONNECTION_")) {
            this.processRequestConnectionRequest(data);
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

            // LOGGER.info("Ping to relay is: {}", millisecondsElapsed);
            ModState.ping.set(millisecondsElapsed);
            this.RTTMonitor.addSample((double) millisecondsElapsed);
        } else {
            processConnectionRequest(data);
        }
    }

    @Override
    protected void handleOpen(ServerHandshake handshake) {
        this.relayPingLoop();
        if (this.flow == SignalConnectionFlow.HostToRelay) {
            this.keepAliveLoop();
        }
    }

    @Override
    protected void handleOnMessage(byte[] data) {
        LOGGER.info("Received {} bytes in signaling", data.length);
        this.processMessageSignaling(data);
    }

    private String getReasonFromCode(int code, String wsReason) {
        String reason = wsReason;

        if (Objects.equals(reason, "") && code == 1006) {
            reason = "ABNORMAL_DISCONNECT";
        }

        return reason;
    }

    @Override
    protected void handleOnClose(int code, String reason, boolean remote) {

        String realReason = getReasonFromCode(code, reason);

        if (this.flow == SignalConnectionFlow.HostToRelay) {
            for (Map.Entry<Channel, WebRTCClient> clients : ModState.channelToRTCClient.entrySet()) {
                LOGGER.info("Disconnecting RTC Client. Cause: signal disconnected. Preserve state.");
                clients.getValue().disconnect();
            }

            LOGGER.info("RELAY SIGNAL disconnected");
            DisconnectHandler.showDisconnectMessageAndRetry(realReason);
        } else {
            LOGGER.info("Not warning for client signal disconnect");
        }
    }


    @Override
    protected void handleError(Exception e) {
        LOGGER.error("An error occurred in Signaling WebSocket: ", e);
    }
}
