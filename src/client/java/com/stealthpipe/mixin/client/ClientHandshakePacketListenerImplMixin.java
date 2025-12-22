package com.stealthpipe.mixin.client;

import com.stealthpipe.Config;
import com.stealthpipe.ModState;
import com.stealthpipe.mixin.ConnectionChannelAccessor;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.network.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Duration;
import java.util.function.Consumer;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.MOD_ID);

    @Inject(method="<init>", at=@At("HEAD"))
    private static void onInit(Connection connection, Minecraft minecraft, ServerData serverData, Screen screen, boolean bl, Duration duration, Consumer consumer, LevelLoadTracker levelLoadTracker, TransferState transferState, CallbackInfo ci) {

        if (!ModState.isClientConnectingToStealthServer.get()) return;

        Channel clientChannel = ((ConnectionChannelAccessor) connection).getChannel();

        ModState.relayClientChannel.set(clientChannel);

        LOGGER.info("Detect initial packet handshake");

        // TODO: Inject into the pipeline

    }
}
