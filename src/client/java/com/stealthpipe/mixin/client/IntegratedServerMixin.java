package com.stealthpipe.mixin.client;

import com.stealthpipe.*;
import com.stealthpipe.connection.HostRelayConnector;
import com.stealthpipe.connection.game.GameConnectionInterface;
import com.stealthpipe.enums.ConnectionDisconnectReason;
import io.netty.channel.Channel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {


    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);
    @Unique
    private static final HostRelayConnector connector = new HostRelayConnector();

    @Inject(method="stopServer", at=@At("HEAD"))
    private void stopServer(CallbackInfo ci) {
        boolean wsConnected = ModState.webSocketOpen.get();
        if (wsConnected) {
            if(ModState.relayClient.get() != null) {
                try {
                    ModState.relayClient.get().disconnectWithReason(ConnectionDisconnectReason.LocalServerStopped);
                } catch (Exception e) {
                    LOGGER.error("Failed to send disconnect reason", e);
                }

            }
        }

        for (Map.Entry<Channel, GameConnectionInterface> entry : ModState.channelToGameConnection.entrySet()) {
            LOGGER.info("Disconnected RTC Connection");
            entry.getValue().disconnectWithReason(ConnectionDisconnectReason.LocalServerStopped);
        }
        LOGGER.info("Detected integrated server closed");

        ModState.channelToGameConnection.clear();
        // ModState.channelToRTCClient.clear();

        ModState.resetState();
    }

    @Inject(method="publishServer", at=@At("HEAD"))
    private void injectPublishServer(GameType gameType, boolean bl, int i, CallbackInfoReturnable<Boolean> cir) {
        System.out.println("Server now open to LAN! Connecting via websocket");
        connector.connectToRelay();
    }

}
