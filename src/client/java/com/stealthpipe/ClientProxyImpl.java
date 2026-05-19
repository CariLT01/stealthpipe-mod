package com.stealthpipe;

import com.stealthpipe.connection.DisconnectHandler;
import com.stealthpipe.connection.HostRelayConnector;
import com.stealthpipe.interfaces.ClientProxy;
import com.stealthpipe.other.UXHelper;
import com.stealthpipe.ui.ConnectionStatusInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;

public class ClientProxyImpl implements ClientProxy {

    private void displayDisconnectScreen(String reason) {
        Minecraft.getInstance().execute(() -> {
            assert Minecraft.getInstance().screen != null;
            Minecraft.getInstance().setScreen(new DisconnectedScreen(Minecraft.getInstance().screen, Component.literal("StealthPipe"), Component.literal(reason)));
        });
    }

    @Override
    public void sendStealthPipeMessage(Component message) {
        Minecraft.getInstance().execute(() -> {
            UXHelper.sendStealthPipeSystemMessage(message);
        });
    }

    @Override
    public void disconnectWithReason(Component reason, int delayInMs) {
        // Cursed way to show a disconnect message, please don't copy
        if (delayInMs > 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(delayInMs);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                displayDisconnectScreen(reason.getString());
            }).start();
        } else {
            displayDisconnectScreen(reason.getString());
        }


    }

    @Override
    public void connectToRelay() {
        if (!ModState.gameOpenToLan.get()) {
            System.out.println("Cancel reconnect attempt, game not open to lan");
            return;
        }
        HostRelayConnector connector = new HostRelayConnector();
        connector.connectToRelay();
    }

    @Override
    public void runOnClientThread(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }

    @Override
    public void resizeConnectionStatusList(int newSize) {
        ConnectionStatusInterface.setConnectionStatusLength(newSize);
    }

    @Override
    public void setConnectionStatusIndex(Component text, int index) {
        ConnectionStatusInterface.setConnectionStatusText(text, index);
    }

    @Override
    public void showDisconnectScreen(Component message) {
        DisconnectHandler.showClientDisconnectMessage(true, message.getString());
    }
}

