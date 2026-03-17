package com.stealthpipe.connection.signal;

public enum SignalingMessageType {
    IDLE(0),
    PING(1),
    PONG(2),
    WebRTC_HandshakeMessage(3),
    WebRTC_ConnectionEstablished(4),
    WebRTC_RequestConnection(5),
    WebRTC_ConnectionFailed(6),
    WebRTC_ConnectionReady(7),
    SignalDisconnect(8);

    private final int signalingPacketType;

    SignalingMessageType(int levelCode) {
        this.signalingPacketType = levelCode;
    }

    public int getPacketType() {
        return this.signalingPacketType;
    }
}
