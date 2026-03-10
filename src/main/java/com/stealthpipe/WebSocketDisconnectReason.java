package com.stealthpipe;

public enum WebSocketDisconnectReason {
    NettyChannelInactiveClient("CLIENT_CHANNEL_INACTIVE"),
    NettyChannelInactiveServer("SERVER_CHANNEL_INACTIVE"),
    FabricEventDisconnectClient("FABRIC_CLIENT_DISCONNECT"),
    LocalServerStopped("LOCAL_SERVER_STOPPED"),
    SignalingWebRTCFailed("SIGN_WRTC_FAILED"),
    SignalingFinished("SIGN_FINISHED");


    private final String signalingDisconnectReason;

    WebSocketDisconnectReason(String disconnectReason) {
        this.signalingDisconnectReason = disconnectReason;
    }

    public String getPacketType() {
        return this.signalingDisconnectReason;
    }
}
