package com.stealthpipe.connection.game;

import com.stealthpipe.enums.ConnectionDisconnectReason;

public interface GameConnectionInterface {
    /**
     * Function that handles the connection establishment of the Game socket.
     * The specific implementation depends on the underlying protocol.
     *
     * @throws Exception An exception is thrown if any part of the connection process fails.
     */
    void connect() throws Exception;


    /**
     * Disconnects the Game connection without a reason. This should never be used.
     *
     */
    void disconnect();

    /**
     * Disconnects the game connection socket with a specified reason. Note that some protocol implementations may not support
     * custom close frames.
     *
     * @param reason The reason that this socket needs to be disconnected.
     */
    void disconnectWithReason(ConnectionDisconnectReason reason);

    /**
     * Queues a packet to be sent. In some implementations, the packet is sent immediately.
     *
     * @param packetData The packet buffer
     */
    void sendPacket(byte[] packetData);
}
