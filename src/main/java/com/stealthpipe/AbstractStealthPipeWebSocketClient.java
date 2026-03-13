package com.stealthpipe;

import com.terraformersmc.modmenu.util.mod.Mod;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class AbstractStealthPipeWebSocketClient extends WebSocketClient {
    public boolean connected = false;
    private final List<byte[]> queuedPackets = new ArrayList<>();

    static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private final ReentrantLock writeLock = new ReentrantLock();

    private final List<Consumer<byte[]>> hookedEvents = new ArrayList<>();

    public AbstractStealthPipeWebSocketClient(String gameId, boolean host, Optional<String> request, boolean clientSignal) {

        super(Utils.formatWebSocketJoinURL(gameId, host, request,clientSignal));
    }

    public void hookOnMessage(Consumer<byte[]> consumer) {
        this.hookedEvents.add(consumer);
    }

    private void sendQueuedPackets() {
        for (byte[] packet : this.queuedPackets) {
            LOGGER.debug("Sent {} queued bytes", packet.length);
            this.send(packet);
        }
        this.queuedPackets.clear();
    }

    public void disconnectWithReason(WebSocketDisconnectReason reason) {
        LOGGER.info("Close called with reason: {}", reason.getPacketType());
        this.close(1000, reason.getPacketType());
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        setTcpNoDelay(true);

        this.connected = true;
        this.sendQueuedPackets();
        handleOpen(handshakeData);

    }

    @Override
    public void send(byte[] data) {
        if (this.connected) {
            writeLock.lock();
            super.send(data);
            writeLock.unlock();
            ModState.outboundPPSCounter.getAndAdd(1);
            ModState.outboundBandwidthCounter.getAndAdd(data.length);
            ModState.outboundData.getAndAdd(data.length);
        } else {
            LOGGER.debug("Queued {} bytes for sending", data.length);
            this.queuedPackets.add(data);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.info("WebSocket closed");
        this.connected = false;

        handleOnClose(code, reason, remote);
    }

    @Override
    public void onError(Exception e) {
        LOGGER.error("An error occurred: ", e);
        handleError(e);
    }

    @Override
    public void onMessage(ByteBuffer byteBuf) {


        byte[] data = new byte[byteBuf.remaining()];
        byteBuf.get(data);
        // LOGGER.info("Received {} bytes", data.length);


        ModState.inboundData.getAndAdd(data.length);
        ModState.inboundBandwidthCounter.getAndAdd(data.length);
        ModState.inboundPPSCounter.getAndAdd(1);

        // LOGGER.info("Added to counter: {}", ModState.inboundData.get());

        for (Consumer<byte[]> consumer : this.hookedEvents) {
            consumer.accept(data);
        }

        handleOnMessage(data);
    }

    @Override
    public void close() {
        LOGGER.info("Called close on WebSocket connection");
        this.connected = false;
    }

    @Override
    public void onMessage(String message) {
        LOGGER.warn("Received string message, ignored it");
    }

    protected abstract void handleOpen(ServerHandshake handshake);
    protected abstract void handleOnMessage(byte[] data);
    protected abstract void handleOnClose(int code, String reason, boolean remote);
    protected abstract void handleError(Exception e);


}
