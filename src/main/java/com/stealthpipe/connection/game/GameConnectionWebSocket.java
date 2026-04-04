package com.stealthpipe.connection.game;

import com.stealthpipe.*;
import com.stealthpipe.connection.AbstractStealthPipeWebSocketClient;
import com.stealthpipe.connection.DisconnectHandler;
import com.stealthpipe.connection.PacketBatchingManager;
import com.stealthpipe.connection.adapters.ChannelReader;
import com.stealthpipe.connection.debug.DataDirection;
import com.stealthpipe.connection.debug.LatencySpikeTest;
import com.stealthpipe.enums.ConnectionDisconnectReason;
import com.stealthpipe.enums.PacketFlow;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class GameConnectionWebSocket extends AbstractStealthPipeWebSocketClient implements GameConnectionInterface {
    private final PacketFlow flow;

    private final Queue<byte[]> queuedSendPackets = new ConcurrentLinkedQueue<>();
    private final Channel gameChannel;
    private boolean gotMessages = false;


    private final PacketBatchingManager packetBatchingManager = new PacketBatchingManager(this::send);

    /**
     * Creates a game connection using the WebSocket protocol.
     *
     * @param gameId Room ID
     * @param flow The direction the packets flow in
     * @param channel The underlying Netty channel to read and write from.
     * @param request The request ID if applicable (for pairing two WebSockets)
     */

    public GameConnectionWebSocket(String gameId, PacketFlow flow, Channel channel, @Nullable String request) {
        super(gameId, flow == PacketFlow.HostToClient, Optional.ofNullable(request), false);

        this.flow = flow;
        this.gameChannel = channel;
    }

    /**
     * Queues a packet to be sent by the PacketBatchingManager.
     * Use this function for any Minecraft packets.
     *
     * @param packet The packet buffer
     */
    public void sendPacket(byte[] packet) {
        this.packetBatchingManager.queuePacket(packet);
    }

    private void reportConnectionStatus(Component text, int index) {
        if (this.flow == PacketFlow.ClientToHost) {
            StealthPipe.CLIENT_PROXY.setConnectionStatusIndex(text, index);
        }
    }

    private void clearConnectionStatus() {
        if (this.flow == PacketFlow.ClientToHost) {
            StealthPipe.CLIENT_PROXY.resizeConnectionStatusList(0);
        }
    }

    private void simulateFailure() {
        if (!StealthPipe.config.SIMULATE_ABNORMAL_DISCONNECT_HOST) return;
        LOGGER.info("Simulating abnormal disconnect failure for game connection");

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
        this.packetBatchingManager.run();

        this.clearConnectionStatus();

        if (this.flow == PacketFlow.ClientToHost) {
            ModState.webSocketOpen.set(true);
            this.simulateFailure();
        }
    }

    @Override
    protected void handleError(Exception e) {
        LOGGER.error("Error occurred in WebSocket tunnel: ", e);
        if (this.flow == PacketFlow.HostToClient) {
            MinecraftServer server = ModState.minecraftServer.get();

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[StealthPipe] Error occurred in tunnel. Please restart the world and create a new session.").withStyle(ChatFormatting.RED),
                    false
            );
        }
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

        this.clearConnectionStatus();

        String displayedReason = this.getReasonFromCode(code, reason);

        // In any case, the channel represents the player connection. Disconnect it.
        this.gameChannel.disconnect();
        this.packetBatchingManager.stop();

        // Display a message on the client
        if (this.flow == PacketFlow.ClientToHost) {
            if (Objects.equals(reason, ConnectionDisconnectReason.NettyChannelInactiveClient.getPacketType())) {
                // Don't show an error message
                // Minecraft should show an error message on top,
                // Or it was an intentional disconnect (Fabric's disconnect event still won't work, caused by a bug)
                // https://github.com/FabricMC/fabric-api/issues/1300
                return;

            }
            DisconnectHandler.showClientDisconnectMessage(this.gotMessages, displayedReason);
        } else {
            LOGGER.info("Not displaying disconnect message on host");
        }
    }

    @Override
    protected void handleOnMessage(byte[] data) {

        // yield
        LatencySpikeTest.yield(DataDirection.RECEIVE);

        this.gotMessages = true;

        List<byte[]> packets = PacketBatchingManager.unpackPacket(data);
        ChannelReader.fireReadsSafer(this.gameChannel, packets);


    }

    public void disconnect() {
        this.disconnectWithReason(ConnectionDisconnectReason.Unknown);
    }
}
