package com.stealthpipe;

public enum ConnectionDisconnectReason {
    NettyChannelInactiveClient("CLIENT_CHANNEL_INACTIVE"),
    NettyChannelInactiveServer("SERVER_CHANNEL_INACTIVE"),
    FabricEventDisconnectClient("FABRIC_CLIENT_DISCONNECT"),
    LocalServerStopped("LOCAL_SERVER_STOPPED"),
    SignalingWebRTCFailed("SIGN_WRTC_FAILED"),
    SignalingFinished("SIGN_FINISHED"),
    Unknown("UNKNOWN"),
    SignalConnectionDisconnected("SIGNAL_DISCONNECTED");


    private final String signalingDisconnectReason;

    ConnectionDisconnectReason(String disconnectReason) {
        this.signalingDisconnectReason = disconnectReason;
    }

    public String getPacketType() {
        return this.signalingDisconnectReason;
    }
}
