package com.stealthpipe;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public class StealthRelay extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // At this point in the pipeline, 'msg' should be a ByteBuf
        // because we are injecting after the Minecraft 'encoder'.
        if (msg instanceof ByteBuf buf) {
            // 1. Peek at the bytes without moving the readerIndex
            // Use 'slice' or 'copy' to ensure you don't mess up the original buffer
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);

            // 2. FORWARD TO RELAY
            // Example: MyRelayClient.send(bytes);
            System.out.println("Relaying " + bytes.length + " bytes to external server...");
        }

        // 3. IMPORTANT: Forward the write call down the pipeline
        super.write(ctx, msg, promise);
    }
}
