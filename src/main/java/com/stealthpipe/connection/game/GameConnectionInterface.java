package com.stealthpipe.connection.game;

import com.stealthpipe.ConnectionDisconnectReason;

public interface GameConnectionInterface {
    void connect() throws Exception;
    void disconnect();
    void disconnectWithReason(ConnectionDisconnectReason reason);
    void sendPacket(byte[] packetData);
}
