package com.stealthpipe.connection.signal;

import com.stealthpipe.*;
import com.stealthpipe.connection.AbstractStealthPipeWebSocketClient;
import com.stealthpipe.connection.DisconnectHandler;
import com.stealthpipe.connection.PacketBatchingManager;
import com.stealthpipe.connection.adapters.ChannelReader;
import com.stealthpipe.connection.debug.DataDirection;
import com.stealthpipe.connection.debug.LatencySpikeTest;
import com.stealthpipe.connection.game.GameConnectionInterface;
import com.stealthpipe.connection.game.GameConnectionWebSocket;
import com.stealthpipe.connection.game.WebRTCGameConnection;
import com.stealthpipe.connection.misc.RoundTripTimeMonitor;
import com.stealthpipe.enums.ConnectionDisconnectReason;
import com.stealthpipe.enums.PacketFlow;
import com.stealthpipe.enums.SignalConnectionFlow;
import com.stealthpipe.enums.SignalingMessageType;
import com.stealthpipe.interfaces.IConnectionInjector;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;

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

    private void reportStatus(Component text, int length) {
        if (this.flow != SignalConnectionFlow.HostToRelay) return;
        StealthPipe.CLIENT_PROXY.setConnectionStatusIndex(text ,length);
    }
    private void clearStatus() {
        if (this.flow != SignalConnectionFlow.HostToRelay) return;
        StealthPipe.CLIENT_PROXY.resizeConnectionStatusList(0);
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
                    this.sentBegin.set(System.nanoTime());
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

    private void handleRTCDisconnect(WebRTCGameConnection client) {

        // It is connection on server

        ModState.channelToGameConnection.entrySet().removeIf(entry -> {
            if (entry.getValue() == client) {
                LOGGER.info("Closed Netty channel on the server, and queued for removal");
                entry.getKey().disconnect(); // Close the Netty channel
                return true; // Removes this entry from the map
            }
            return false;
        });
    }

    private EmbeddedChannel createVirtualChannel() {
        MinecraftServer server = ModState.minecraftServer.get();

        EmbeddedChannel virtualChannel = new EmbeddedChannel();
        ServerConnectionListener listener = server.getConnection();

        ((IConnectionInjector) listener).injectVirtualConnection(virtualChannel);

        return virtualChannel;
    }

    private void processWebRTCRequestConnectionRequest(byte[] data) {
        try {
            LOGGER.info("Received a WebRTC Request Connection signal");

            byte clientId = data[1];

            if (!StealthPipe.config.HOST_ALLOW_WEBRTC_INBOUND) {
                // refuse request

                LOGGER.info("Refused WebRTC connection request, as stated in config");
                byte[] sendBackData = new byte[]{(byte) SignalingMessageType.WebRTC_ConnectionFailed.getPacketType(), clientId};

                this.send(sendBackData);
                return;
            }

            EmbeddedChannel virtualChannel = this.createVirtualChannel();

            WebRTCGameConnection rtcClient = new WebRTCGameConnection((byte[] message) -> {
                // yield
                LatencySpikeTest.yield(DataDirection.RECEIVE);
                List<byte[]> packets = PacketBatchingManager.unpackPacket(message);
                ChannelReader.fireReadsSafer(virtualChannel, packets);


            }, this::handleRTCDisconnect, PacketFlow.HostToClient, this, clientId);

            byte[] readyData = new byte[]{(byte)SignalingMessageType.WebRTC_ConnectionReady.getPacketType(), clientId};
            this.send(readyData);
            LOGGER.info("Sent ready data");

            try {
                rtcClient.connect();
                // success
                LOGGER.info("RTC Client established!");
                ModState.channelToGameConnection.put(virtualChannel, rtcClient);
            } catch (Exception e) {
                LOGGER.error("RTC connection failed to establish");
                this.send(this.prepareSignalingMessage(clientId, (byte)SignalingMessageType.WebRTC_ConnectionFailed.getPacketType(), new byte[0]));
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to instantiate WebRTC:", e);
        }




    }

    private void processRequestConnectionRequest(byte[] data) {
        // We need to create a fake channel

        String newString = new String(data, StandardCharsets.UTF_8);

        EmbeddedChannel virtualChannel = this.createVirtualChannel();

        String url = String.format(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=%s&host=true&request=%s&version=%s", this.gameId, newString, StealthPipe.MOD_VERSION);

        // StealthWebSocketClient newClient = new StealthWebSocketClient(URI.create(url), WebsocketClientType.SERVER_TO_RELAY, virtualChannel, gameId);
        // newClient.connect();

        GameConnectionWebSocket newClient = new GameConnectionWebSocket(gameId, PacketFlow.HostToClient, virtualChannel, newString);
        newClient.connect();

        ModState.channelToGameConnection.put(virtualChannel, newClient);

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

    private void simulateFailure() {
        if (!StealthPipe.config.SIMULATE_ABNORMAL_DISCONNECT_HOST) return;
        LOGGER.info("Simulating abnormal disconnect failure for host");

        new Thread(() -> {
            LOGGER.info("Starting abnormal failure...");
            try {
                Thread.sleep(StealthPipe.config.SIMULATED_FAILURE_DELAY * 1000L);
                this.close(1006);
            } catch (Exception e) {
                LOGGER.error("Failed to simulate an abnormal disconnect: ", e);
            } finally {
                LOGGER.info("Successfully simulated an abnormal disconnect");
            }
        }).start();
    }

    @Override
    protected void handleOpen(ServerHandshake handshake) {
        clearStatus();
        this.relayPingLoop();
        if (this.flow == SignalConnectionFlow.HostToRelay) {
            this.keepAliveLoop();
            this.simulateFailure();
        }

        if (StealthPipe.config.LATENCY_SPIKES) {
            LOGGER.warn("Starting artificial latency spikes");
            LatencySpikeTest.run();
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
        clearStatus();

        String realReason = getReasonFromCode(code, reason);

        if (this.flow == SignalConnectionFlow.HostToRelay) {
            /*
            Don't disconnect WebRTC connections when SIGNAL disconnects by default
            for (Map.Entry<Channel, GameConnectionInterface> clients : ModState.channelToGameConnection.entrySet()) {
                LOGGER.info("Disconnecting RTC Client. Cause: signal disconnected. Preserve state.");
                clients.getValue().disconnectWithReason(ConnectionDisconnectReason.SignalConnectionDisconnected);
            } */

            // Do disconnect WebSocket connections
            // This is done on the relay-side too, but it's better to do it on the client just for redundancy
            for (Map.Entry<EmbeddedChannel, GameConnectionInterface> client : ModState.channelToGameConnection.entrySet()) {
                if (client.getValue() instanceof GameConnectionWebSocket) {
                    LOGGER.info("Disconnected WebSocket client");
                    client.getValue().disconnectWithReason(ConnectionDisconnectReason.SignalConnectionDisconnected);
                }
            }

            // Unless if the disconnect reason is FABRIC_CLIENT_DISCONNECT or LOCAL_SERVER_STOPPED, don't disconnect WebRTC.
            // These reasons should be present if the host is the one that leaves the game.
            // This logic should also trigger in the integrated server mixin, but sometimes it's not reliable.

            // This allows WebRTC clients to keep playing. The relay doesn't see WebRTC clients, so it constantly disconnects
            // the room because it thinks it's empty. Should provide a better user experience.
            if (Objects.equals(reason, ConnectionDisconnectReason.FabricEventDisconnectClient.getPacketType()) ||
                    Objects.equals(reason, ConnectionDisconnectReason.LocalServerStopped.getPacketType())) {
                for (Map.Entry<EmbeddedChannel, GameConnectionInterface> client : ModState.channelToGameConnection.entrySet()) {
                    if (client.getValue() instanceof WebRTCGameConnection) {
                        LOGGER.info("Disconnecting WebRTC client");
                        client.getValue().disconnectWithReason(ConnectionDisconnectReason.SignalConnectionDisconnected);
                    }
                }
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
