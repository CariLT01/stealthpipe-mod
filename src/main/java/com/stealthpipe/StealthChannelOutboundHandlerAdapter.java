package com.stealthpipe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.HiddenByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Unique;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class StealthChannelOutboundHandlerAdapter extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.MOD_ID);

    private static final Set<Channel> warnedIDs = new HashSet<>();
    private final String label;

    public StealthChannelOutboundHandlerAdapter(String label) {
        super();

        this.label = label;
    }

    private byte[] concatUuidAndData(String uuid, byte[] data) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        buffer.writeBytes(uuid.getBytes(StandardCharsets.UTF_8));
        buffer.writeBytes(data);
        byte[] result = new byte[buffer.readableBytes()];
        buffer.readBytes(result);
        buffer.release();
        return result;

    }

    @Unique
    private void forwardDataToRelay(byte[] bytes, StealthWebSocketClient wsClient, Channel destination) {

        boolean isClient = ModState.isClientConnectingToStealthServer.get();

        if (isClient) {
            // Send to server, with MY relay client uuid

            String myUuid = ModState.relayClientUuid.get();

            byte[] packet = concatUuidAndData(myUuid, bytes);


            wsClient.send(packet);

        } else {
            // Send it to the client, with relay client UUID

            String clientUuid = ModState.minecraftChannelUuidToRelayUuidMap.get(destination);

            if (clientUuid == null) {
                return;
            }

            byte[] packet = concatUuidAndData(clientUuid, bytes);

            wsClient.send(packet);
        }
    }

    @Unique
    private boolean handleRelayForwarding(String label, Object msg, Channel destination) {

        StealthWebSocketClient wsClient = ModState.relayClient.get();

        if (wsClient == null) {
            return false;
        }
        boolean isClient = ModState.isClientConnectingToStealthServer.get();

        if (!isClient && !ModState.minecraftChannelUuidToRelayUuidMap.containsKey(destination)) {

            if (!warnedIDs.contains(destination)) {
                LOGGER.warn("Destination does not have corresponding mapped relay ID");

                warnedIDs.add(destination);
            }

            return false;
        }



        if (msg instanceof HiddenByteBuf buf) {
            ByteBuf byteBuf = buf.contents();
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.getBytes(byteBuf.readerIndex(), bytes);


            this.forwardDataToRelay(bytes, wsClient, destination);


        } else if (msg instanceof ByteBuf buf) {

            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);

            this.forwardDataToRelay(bytes, wsClient, destination);

        }

        else {
            System.out.printf("%s: Unknown type: %s%n", label, msg.getClass().getName());
        }

        return true;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {



        Channel id = ctx.channel();

        // System.out.printf("Writing to: %s%n", id);

        boolean isWSConnected = ModState.webSocketOpen.get();

        if (isWSConnected) {
            boolean success = handleRelayForwarding(label, msg, id);

            promise.setSuccess();

            if (success) {
                return;
            }

            // Don't return if it isn't success, probably means it is the original client, so forward the writing back to the original pipeline.

        }


        super.write(ctx, msg, promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        boolean isClient = ModState.isClientConnectingToStealthServer.get();
        boolean wsConnected = ModState.webSocketOpen.get();

        if (!wsConnected) return;

        if (isClient) {
            ModState.relayClient.get().close();
            ModState.webSocketOpen.set(false);
            ModState.relayClient.set(null);

            LOGGER.info("Connection closed, detected channel inactive");
        } else {

            // Kicked player, send it to relay to disconnect the client-server connection

            String clientUUID = ModState.minecraftChannelUuidToRelayUuidMap.get(ctx.channel());

            if (clientUUID == null) {
                LOGGER.error("Failed to disconnect client, UUID not found");
            }

            String packet = "CLOSECONNECTION_" + clientUUID;
            byte[] packetAsBytes = packet.getBytes(StandardCharsets.UTF_8);

            ModState.relayClient.get().send(packetAsBytes);

            LOGGER.info("Sent signal to relay to disconnect connection");

        }

    }
}
