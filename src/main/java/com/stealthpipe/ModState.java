package com.stealthpipe;

import io.netty.channel.Channel;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public static void resetState() {

    }
}
