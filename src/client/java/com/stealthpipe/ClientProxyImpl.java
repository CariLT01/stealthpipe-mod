package com.stealthpipe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;

import java.util.Objects;

import static net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl.LOGIN;
import static net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl.PLAY;

public class ClientProxyImpl implements ClientProxy {

    @Override
    public void disconnectWithReason(String reason) {
        // Cursed way to show a disconnect message, please don't copy
        Minecraft.getInstance().execute(() -> {
            assert Minecraft.getInstance().screen != null;
            Minecraft.getInstance().setScreen(new DisconnectedScreen(Minecraft.getInstance().screen, Component.literal("StealthPipe"), Component.literal(reason)));
        });
    }
}

