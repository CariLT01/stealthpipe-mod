package com.stealthpipe.mixin.client;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.stealthpipe.*;
import com.terraformersmc.modmenu.util.mod.Mod;
import io.netty.channel.Channel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {


    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);
    @Unique
    private static final RelayConnector connector = new RelayConnector();

    @Inject(method="stopServer", at=@At("HEAD"))
    private void stopServer(CallbackInfo ci) {
        boolean wsConnected = ModState.webSocketOpen.get();
        if (wsConnected) {
            if(ModState.relayClient.get() != null) {
                ModState.relayClient.get().close();
            }
            for (Map.Entry<Channel, WebRTCClient> entry : ModState.channelToRTCClient.entrySet()) {
                LOGGER.info("Disconnected RTC Connection");
                entry.getValue().disconnect();
            }
            LOGGER.info("Detected integrated server closed");
        }

        ModState.channelToWSClient.clear();
        ModState.channelToRTCClient.clear();

        ModState.resetState();
    }

    @Inject(method="publishServer", at=@At("HEAD"))
    private void injectPublishServer(GameType gameType, boolean bl, int i, CallbackInfoReturnable<Boolean> cir) {
        System.out.println("Server now open to LAN! Connecting via websocket");
        connector.connectToRelay();
    }

}
