package com.stealthpipe.connection.game;

import com.stealthpipe.*;
import com.stealthpipe.connection.AbstractStealthPipeWebSocketClient;
import com.stealthpipe.connection.DisconnectHandler;
import com.stealthpipe.connection.PacketBatchingManager;
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

public class GameConnectionWebSocket extends AbstractStealthPipeWebSocketClient implements GameConnectionInterface {
    private final PacketFlow flow;

    private final Queue<byte[]> queuedSendPackets = new ConcurrentLinkedQueue<>();
    private final Channel gameChannel;
    private boolean gotMessages = false;

    private final PacketBatchingManager packetBatchingManager = new PacketBatchingManager(this::send);

    public GameConnectionWebSocket(String gameId, PacketFlow flow, Channel channel, @Nullable String request) {
        super(gameId, flow == PacketFlow.HostToClient, Optional.ofNullable(request), false);

        this.flow = flow;
        this.gameChannel = channel;
    }

    public void sendPacket(byte[] packet) {
        this.packetBatchingManager.queuePacket(packet);
    }

    private void reportConnectionStatus(String text, int index) {
        if (this.flow == PacketFlow.ClientToHost) {
            StealthPipe.CLIENT_PROXY.setConnectionStatusIndex(text, index);
        }
    }

    private void clearConnectionStatus() {
        if (this.flow == PacketFlow.ClientToHost) {
            StealthPipe.CLIENT_PROXY.resizeConnectionStatusList(0);
        }
    }

    @Override
    protected void handleOpen(ServerHandshake handshake) {
        this.packetBatchingManager.run();

        this.clearConnectionStatus();

        if (this.flow == PacketFlow.ClientToHost) {
            ModState.webSocketOpen.set(true);
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
        this.gotMessages = true;

        List<byte[]> packets = this.packetBatchingManager.unpackPacket(data);

        for (byte[] packet : packets) {
            // In any case (either on Host or Client), the channel should be the player channel
            // So we should only just need to fire a read

            this.gameChannel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(packet));
        }

    }

    public void disconnect() {
        this.disconnectWithReason(ConnectionDisconnectReason.Unknown);
    }
}
