package com.stealthpipe;

public interface ClientProxy {
    void disconnectWithReason(String reason, int delayInMs);
    void connectToRelay();
    void sendStealthPipeMessage(String message);
}
