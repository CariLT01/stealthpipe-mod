package com.stealthpipe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class PacketBatchingManager {

    private Queue<byte[]> queuedSendPackets = new ConcurrentLinkedQueue<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private final long BATCHING_INTERVAL = StealthPipe.config.PACKET_BATCHING_INTERVAL_MS * 1_000_000L;

    private boolean running = false;

    private Consumer<byte[]> sendConsumer;

    public PacketBatchingManager(Consumer<byte[]> sendConsumer) {
        this.sendConsumer = sendConsumer;
    }

    public void run() {
        this.running = true;
        this.sendLoop();
    }

    public void stop() {
        this.running = false;
    }

    public void queuePacket(byte[] packet) {
        this.queuedSendPackets.add(packet);
    }

    public static List<byte[]> unpackPacket(byte[] packedData) {
        List<byte[]> packets = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(packedData);

        while (buffer.remaining() >= 4) {
            int packetLength = buffer.getInt();

            if (packetLength < 0 || packetLength > buffer.remaining()) {
                LOGGER.error("Failed to parse packet batch: invalid packet length");
                break;
            }

            byte[] packetData = new byte[packetLength];
            buffer.get(packetData);

            packets.add(packetData);
        }

        return packets;
    }

    private void sendQueuedSendPacketsBatched() {
        int PACKET_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB

        while (!this.queuedSendPackets.isEmpty()) {
            // Create a composite buffer to hold the entire batch
            CompositeByteBuf batchBuffer = Unpooled.compositeBuffer();

            try {
                while (!this.queuedSendPackets.isEmpty()) {
                    byte[] packet = this.queuedSendPackets.peek();
                    if (packet == null) break;

                    // Check for size limit: (existing buffer + 4 byte header + packet length)
                    if (batchBuffer.readableBytes() + packet.length + 4 > PACKET_SIZE_LIMIT) {
                        if (batchBuffer.readableBytes() > 0) break;
                        // If single packet > limit, we let it through once so it doesn't clog
                    }

                    // Remove from queue now that we're committed
                    this.queuedSendPackets.poll();

                    // 1. Create a 4-byte buffer for the length header
                    ByteBuf header = Unpooled.copyInt(packet.length);
                    // 2. Wrap the existing packet array (no copy!)
                    ByteBuf body = Unpooled.wrappedBuffer(packet);

                    // Add to composite (true = advance writer index)
                    batchBuffer.addComponents(true, header, body);
                }

                if (batchBuffer.readableBytes() > 0) {
                    // Flatten once for the WebSocket send
                    byte[] flatBatch = new byte[batchBuffer.readableBytes()];
                    batchBuffer.readBytes(flatBatch);

                    ModState.outboundPPSCounter.getAndAdd(1);
                    this.sendConsumer.accept(flatBatch);
                }
            } catch (Exception e) {
                LOGGER.error("Batching error: ", e);
                this.queuedSendPackets.clear();
            } finally {
                batchBuffer.release(); // Free memory
            }
        }
    }

    private void sendQueuedSendPackets() {
        if (StealthPipe.config.ENABLE_BATCHED_PACKETS) {
            this.sendQueuedSendPacketsBatched();
        } else {
            // We use a Composite buffer to avoid copying data until the last possible second
            CompositeByteBuf composite = Unpooled.compositeBuffer();

            try {
                while (!this.queuedSendPackets.isEmpty()) {
                    byte[] data = this.queuedSendPackets.poll();
                    if (data == null) break;

                    // Add 4-byte length + payload
                    ByteBuf header = Unpooled.copyInt(data.length);
                    ByteBuf body = Unpooled.wrappedBuffer(data);

                    // 'true' means update the writer index immediately
                    composite.addComponents(true, header, body);
                }

                if (composite.readableBytes() > 0) {
                    // Since super.send(byte[]) needs an array, we have to flatten it once here
                    byte[] flat = new byte[composite.readableBytes()];
                    composite.readBytes(flat);

                    this.sendConsumer.accept(flat);
                }
            } finally {
                // Netty buffers are reference-counted. You MUST release or leak memory.
                composite.release();
            }
        }
    }

    private void sendLoop() {
        LOGGER.info("Starting send loop");
        new Thread(() -> {
            while (this.running) {
                long start = System.nanoTime();
                this.sendQueuedSendPackets();
                long elapsed = System.nanoTime() - start;
                long toWait = this.BATCHING_INTERVAL - elapsed;
                if (toWait > 0) {
                    if (toWait > 2_000_000L) { // >2ms -> park to save CPU
                        LockSupport.parkNanos(toWait - 500_000L); // park most of it
                    }
                    // short busy-spin to improve precision for the remaining nanos
                    while (System.nanoTime() - start < this.BATCHING_INTERVAL) {
                        Thread.onSpinWait();
                    }
                }
            }
        }).start();
    }


}
