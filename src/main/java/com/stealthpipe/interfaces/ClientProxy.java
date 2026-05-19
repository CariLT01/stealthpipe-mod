package com.stealthpipe.interfaces;

import net.minecraft.network.chat.Component;

public interface ClientProxy {
    void disconnectWithReason(Component reason, int delayInMs);
    void connectToRelay();
    void sendStealthPipeMessage(Component message);
    void runOnClientThread(Runnable runnable);
    void setConnectionStatusIndex(Component text, int index);
    void resizeConnectionStatusList(int newSize);

    void showDisconnectScreen(Component message);

}
