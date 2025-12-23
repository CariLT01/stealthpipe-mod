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
    public static AtomicReference<Channel> serverChannel = new AtomicReference<>(null);
    public static AtomicReference<String> gameId = new AtomicReference<>("");
    public static AtomicBoolean webSocketOpen = new AtomicBoolean(false);
    public static ConcurrentHashMap<Channel, String> minecraftChannelUuidToRelayUuidMap = new ConcurrentHashMap<>();
    public static AtomicReference<String> pendingChannelUuid = new AtomicReference<>("");
    public static AtomicBoolean isClientConnectingToStealthServer = new AtomicBoolean(false);
    public static AtomicReference<Channel> relayClientChannel = new AtomicReference<>(null);
    public static ConcurrentHashMap<String, Channel> relayUuidToChannelMap = new ConcurrentHashMap<>();
    public static AtomicReference<String> relayClientUuid = new AtomicReference<>("");
    public static AtomicReference<MinecraftServer> minecraftServer = new AtomicReference<>(null);

    // Allows client executor to be accessible in common code
    public static AtomicReference<Executor> clientThreadExecutor = new AtomicReference<>(null);
}
