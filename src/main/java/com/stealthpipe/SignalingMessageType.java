package com.stealthpipe;

public enum SignalingMessageType {
    PING(1),
    PONG(2),
    WebRTC_HandshakeMessage(3),
    WebRTC_ConnectionEstablished(4);

    private final int signalingPacketType;

    SignalingMessageType(int levelCode) {
        this.signalingPacketType = levelCode;
    }

    public int getPacketType() {
        return this.signalingPacketType;
    }
}
