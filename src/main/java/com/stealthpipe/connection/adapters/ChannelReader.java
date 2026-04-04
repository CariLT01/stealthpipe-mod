package com.stealthpipe.connection.adapters;

import com.stealthpipe.StealthPipe;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.util.List;

public class ChannelReader {
    public static void fireOne(Channel channel, byte[] packet) {
        ByteBuf buf = Unpooled.copiedBuffer(packet);

        try {
            channel.pipeline().fireChannelRead(buf);
        } finally {
            if (buf.refCnt() > 0) buf.release(); // Free memory
        }
    }

    public static void fireReadsSafer(Channel channel, List<byte[]> packets) {
        if (StealthPipe.config.USE_SAFE_INJECT) {
            channel.eventLoop().execute(() -> {
                for (byte[] packet : packets) {
                    fireOne(channel, packet);
                }
            });
        } else {
            for (byte[] packet : packets) {
                fireOne(channel, packet);
            }
        }
    }

    public static void fireReadSafer(Channel channel, byte[] packet) {
        if (StealthPipe.config.USE_SAFE_INJECT) {
            channel.eventLoop().execute(() -> {
                fireOne(channel, packet);
            });
        } else {
            fireOne(channel, packet);
        }
    }
}
