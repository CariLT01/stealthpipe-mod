package com.stealthpipe;

public enum SignalingMessageType {
    PING(0),
    PONG(1),
    WebRTC_HandshakeMessage(2),
    WebRTC_ConnectionEstablished(3),
    WebRTC_RequestConnection(4),
    WebRTC_ConnectionFailed(5),
    WebRTC_ConnectionReady(6);

    private final int signalingPacketType;

    SignalingMessageType(int levelCode) {
        this.signalingPacketType = levelCode;
    }

    public int getPacketType() {
        return this.signalingPacketType;
    }
}
