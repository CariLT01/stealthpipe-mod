package com.stealthpipe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stealthpipe.connection.debug.LatencySpikeDirection;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class StealthPipeConfig {

    public String RELAY_IP = DefaultConfigValues.RELAY_IP;
    public int RELAY_PING_ATTEMPTS = DefaultConfigValues.RELAY_PING_ATTEMPTS;
    public boolean ONLINE_MODE = DefaultConfigValues.ONLINE_MODE;
    // public static final String RELAY_IP = "http://127.0.0.1:7860";
    // public static final String RELAY_IP_WS = "ws://127.0.0.1:7860";

    public boolean ENABLE_BATCHED_PACKETS = DefaultConfigValues.ENABLE_BATCHED_PACKETS;
    public int PACKET_BATCHING_INTERVAL_MS = DefaultConfigValues.PACKET_BATCHING_INTERVAL_MS;

    public boolean CLIENT_ATTEMPT_WEBRTC = DefaultConfigValues.CLIENT_ATTEMPT_WEBRTC;
    public boolean HOST_ALLOW_WEBRTC_INBOUND = DefaultConfigValues.HOST_ALLOW_WEBRTC_INBOUND;

    public boolean USE_SAFE_INJECT = DefaultConfigValues.USE_SAFE_INJECT;

    // Debug options
    public boolean SIMULATE_ICE_CANDIDATES_FAILURE = DefaultConfigValues.SIMULATE_ICE_CANDIDATES_FAILURE;
    public boolean LOG_WRTC_ICE_CANDIDATES = DefaultConfigValues.LOG_WRTC_ICE_CANDIDATES;
    public boolean SHOW_CONNECT_INFO = DefaultConfigValues.SHOW_CONNECT_INFO;
    public boolean SIMULATE_ABNORMAL_DISCONNECT_HOST = DefaultConfigValues.SIMULATE_ABNORMAL_DISCONNECT_HOST;
    public boolean SIMULATE_ABNORMAL_DISCONNECT_CLIENT = DefaultConfigValues.SIMULATE_ABNORMAL_DISCONNECT_CLIENT;
    public boolean LATENCY_SPIKES = DefaultConfigValues.LATENCY_SPIKES;
    public LatencySpikeDirection LATENCY_DIRECTION = DefaultConfigValues.LATENCY_DIRECTION;
    public int SIMULATED_FAILURE_DELAY = DefaultConfigValues.SIMULATED_FAILURE_DELAY;
    public int LATENCY_BASELINE = DefaultConfigValues.LATENCY_BASELINE;



    // Readers
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stealthpipe.json");

    public boolean HAS_SHOWN_WEBRTC_PRIVACY_NOTE = DefaultConfigValues.HAS_SHOWN_WEBRTC_PRIVACY_NOTE;

    public static StealthPipeConfig load() {
        if (!Files.exists(PATH)) return new StealthPipeConfig();
        try (Reader reader = Files.newBufferedReader(PATH)) {
            return GSON.fromJson(reader, StealthPipeConfig.class);
        } catch (Exception e) {
            return new StealthPipeConfig();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(this, writer);
        } catch (Exception ignored) {}
    }

}
