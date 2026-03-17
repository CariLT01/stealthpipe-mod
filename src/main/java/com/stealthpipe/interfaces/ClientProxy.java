package com.stealthpipe.interfaces;

public interface ClientProxy {
    void disconnectWithReason(String reason, int delayInMs);
    void connectToRelay();
    void sendStealthPipeMessage(String message);
    void runOnClientThread(Runnable runnable);

}
