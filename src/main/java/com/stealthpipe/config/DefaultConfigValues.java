package com.stealthpipe.config;

import com.stealthpipe.connection.debug.LatencySpikeTest;
import com.stealthpipe.connection.debug.LatencySpikeDirection;

public class DefaultConfigValues {

    public static final String RELAY_IP = "https://mcpipeservice-go.onrender.com";
    public static final int RELAY_PING_ATTEMPTS = 5;
    public static final boolean ONLINE_MODE = true;
    public static final boolean ENABLE_BATCHED_PACKETS = true;
    public static final int PACKET_BATCHING_INTERVAL_MS = 1;
    public static final boolean CLIENT_ATTEMPT_WEBRTC = true;
    public static final boolean HOST_ALLOW_WEBRTC_INBOUND = true;

    public static final boolean USE_SAFE_INJECT = false;

    // Preferences
    public static final boolean SHOW_CONNECT_INFO = true;

    // Messages
    public static final boolean HAS_SHOWN_WEBRTC_PRIVACY_NOTE = false;

    /*DEBUG OPTIONS*/
    public static final boolean SIMULATE_ICE_CANDIDATES_FAILURE = false;
    public static final boolean LOG_WRTC_ICE_CANDIDATES = false;
    public static final boolean SIMULATE_ABNORMAL_DISCONNECT_HOST = false;
    public static final boolean SIMULATE_ABNORMAL_DISCONNECT_CLIENT = false;
    public static final boolean LATENCY_SPIKES = false;
    public static final LatencySpikeDirection LATENCY_DIRECTION = LatencySpikeDirection.DIRECTION_BOTH;
    public static final int SIMULATED_FAILURE_DELAY = 10;
    public static final int LATENCY_BASELINE = 0;


    // public static final String RELAY_IP = "http://127.0.0.1:7860";
    // public static final String RELAY_IP_WS = "ws://127.0.0.1:7860";
}
