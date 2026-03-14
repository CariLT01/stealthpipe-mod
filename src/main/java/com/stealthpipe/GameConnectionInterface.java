package com.stealthpipe;

public interface GameConnectionInterface {
    void connect() throws Exception;
    void disconnect();
    void disconnectWithReason(ConnectionDisconnectReason reason);
    void sendPacket(byte[] packetData);
}
