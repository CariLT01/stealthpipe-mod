package com.stealthpipe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.java_websocket.handshake.ServerHandshake;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

public class GameConnectionWebSocket extends AbstractStealthPipeWebSocketClient {
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

    @Override
    protected void handleOpen(ServerHandshake handshake) {
        this.packetBatchingManager.run();

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


        String displayedReason = this.getReasonFromCode(code, reason);

        // In any case, the channel represents the player connection. Disconnect it.
        this.gameChannel.disconnect();
        // Display a message on the client
        if (this.flow == PacketFlow.ClientToHost) {
            if (Objects.equals(reason, WebSocketDisconnectReason.NettyChannelInactiveClient.getPacketType())) {
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
}
