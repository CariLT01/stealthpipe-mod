package com.stealthpipe;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ModState {

    public static AtomicBoolean gameOpenToLan = new AtomicBoolean(false);
    public static AtomicReference<StealthWebSocketClient> relayClient = new AtomicReference<>(null);
    public static AtomicReference<String> gameId = new AtomicReference<>("");
    public static AtomicBoolean webSocketOpen = new AtomicBoolean(false);

    public static AtomicBoolean isClientConnectingToStealthServer = new AtomicBoolean(false);
    public static AtomicReference<Channel> relayClientChannel = new AtomicReference<>(null);
    public static AtomicReference<MinecraftServer> minecraftServer = new AtomicReference<>(null);

    public static ConcurrentHashMap<Channel, StealthWebSocketClient> channelToWSClient = new ConcurrentHashMap<>();

    // Allows client executor to be accessible in common code
    public static AtomicReference<Executor> clientThreadExecutor = new AtomicReference<>(null);

    public static AtomicInteger outboundData = new AtomicInteger(0);
    public static AtomicInteger inboundData = new AtomicInteger(0);

    public static AtomicInteger inboundBandwidthCounter = new AtomicInteger(0);
    public static AtomicInteger outboundBandwidthCounter = new AtomicInteger(0);

    public static AtomicInteger inboundBandwidth = new AtomicInteger(0);
    public static AtomicInteger outboundBandwidth = new AtomicInteger(0);

    public static AtomicInteger inboundPPSCounter = new AtomicInteger(0);
    public static AtomicInteger outboundPPSCounter = new AtomicInteger(0);

    public static AtomicInteger inboundPPSd = new AtomicInteger(0);
    public static AtomicInteger outboundPPSd = new AtomicInteger(0);

    public static AtomicLong lastBandwidthTick = new AtomicLong(0);

    public static AtomicReference<String> reuseToken = new AtomicReference<>(null); // Reuse token, allows auto-reconnect and keeping the same room code



    public static AtomicReference<Connection> clientConnectionInstance = new AtomicReference<>(null);

    public static void resetState() {
        outboundData.set(0);
        inboundData.set(0);
        isClientConnectingToStealthServer.set(false);
        gameOpenToLan.set(false);
        webSocketOpen.set(false);
        reuseToken.set(null);
    }
}
