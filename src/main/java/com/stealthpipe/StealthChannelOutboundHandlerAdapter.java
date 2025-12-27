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
    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

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
    private boolean forwardDataToRelay(byte[] bytes, Channel destination) {

        boolean isClient = ModState.isClientConnectingToStealthServer.get();





        if (isClient) {
            // Send it to server
            StealthWebSocketClient relayClient = ModState.relayClient.get();

            relayClient.send(bytes);

            // LOGGER.info("Forwarding {} bytes to the relay as a client", bytes.length);

            return true;

        } else {
            // Send it to the client

            StealthWebSocketClient wsClient = ModState.channelToWSClient.get(destination);
            if (wsClient == null) {
                return false;
            }

            wsClient.send(bytes);

            // LOGGER.info("Forwarding {} bytes to the relay as a server", bytes.length);

            return true;
        }
    }

    @Unique
    private boolean handleRelayForwarding(String label, Object msg, Channel destination) {

        boolean isClient = ModState.isClientConnectingToStealthServer.get();



        if (msg instanceof HiddenByteBuf buf) {
            ByteBuf byteBuf = buf.contents();
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.getBytes(byteBuf.readerIndex(), bytes);


            return this.forwardDataToRelay(bytes, destination);


        } else if (msg instanceof ByteBuf buf) {

            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);

            return this.forwardDataToRelay(bytes, destination);

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



            if (success) {
                promise.setSuccess();
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

            ModState.resetState();

            ModState.channelToWSClient.clear();

            LOGGER.info("Connection closed, detected channel inactive");
        } else {

            // Kicked player, close the WS connection

            StealthWebSocketClient wsClient = ModState.channelToWSClient.get(ctx.channel());
            if (wsClient != null) {
                LOGGER.info("2: Connection closed, detected channel inactive");
                wsClient.close();
            } else {
                LOGGER.info("Destination not found, no WS client to close");
            }


        }

    }
}
