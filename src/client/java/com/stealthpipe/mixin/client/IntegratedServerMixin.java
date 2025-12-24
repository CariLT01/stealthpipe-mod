package com.stealthpipe.mixin.client;

import com.google.gson.Gson;
import com.stealthpipe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
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
import java.time.Instant;
import java.util.Objects;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Unique
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);



    @Unique
    private boolean pingRelay() {

        UXHelper.sendSystemMessage("[StealthPipe]: Connecting to the relay...", ChatFormatting.GRAY);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StealthPipe.config.RELAY_IP + "/ping"))
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();



        for (int i = 0; i < StealthPipe.config.RELAY_PING_ATTEMPTS; i++) {

            try {

                if (i != 0) {
                    UXHelper.sendSystemMessage(
                            String.format("[StealthPipe]: Attempt %s...", (i + 1)),
                            ChatFormatting.GRAY
                    );
                }

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (!Objects.equals(response.body(), "OK")) {
                    throw new RuntimeException("Relay did not return string 'OK', received: " + response.body());
                } else {
                    return true;
                }

            } catch (Exception e) {

                LOGGER.error("An error occurred while trying to reach relay: ", e);

                UXHelper.sendSystemMessage(
                        "[StealthPipe]: Failed to reach relay",
                        ChatFormatting.RED
                );
            }



        }

        return false;
    }

    @Unique
    private void hostRelayMessage() {
        UXHelper.sendSystemMessage(
                "To host a relay for higher performance and reduced ping, see: https://github.com/CariLT01/stealthpipe-relay/blob/main/HOSTING.md",
                ChatFormatting.GRAY
        );
    }

    @Unique
    private void getRoundTripLatency() {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(StealthPipe.config.RELAY_IP + "/ping"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();

            long begin = Instant.now().toEpochMilli();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long end = Instant.now().toEpochMilli();

            int pingMs = (int) (end - begin);

            LOGGER.info("Ping: {}ms", pingMs);

            if (pingMs <= 100) {
                UXHelper.sendSystemMessage(
                        String.format("[StealthPipe]: Ping is low (%sms).", pingMs),
                        ChatFormatting.GREEN
                );
            } else if (pingMs <= 150) {
                UXHelper.sendSystemMessage(
                        String.format("[StealthPipe]: Warning: ping to relay is moderately high (%sms).", pingMs),
                        ChatFormatting.YELLOW
                );
                hostRelayMessage();
            } else if (pingMs <= 250) {
                UXHelper.sendSystemMessage(
                        String.format("[StealthPipe]: Warning: ping to relay is high (%sms).", pingMs),
                        ChatFormatting.RED
                );
                hostRelayMessage();
            } else {
                // if pingMs > 250
                UXHelper.sendSystemMessage(
                        String.format("[StealthPipe]: Warning: ping to relay is extremely high (%sms). It is highly recommended to host your own relay or switch relay.", pingMs),
                        ChatFormatting.RED
                );
                hostRelayMessage();
            }


        } catch (Exception e) {
            LOGGER.error("Failed to measure round-trip latency: ", e);
        }


    }

    @Unique
    private void establishConnection(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assert response.statusCode() == 200;

        System.out.printf("Field data is: %s%n", response.body());

        assert !response.body().isEmpty();

        ResponseModel data = GSON.fromJson(response.body(), ResponseModel.class);

        if (data == null) {
            throw new IllegalArgumentException("Server returned invalid JSON!");
        }

        if (data.ok) {
            String gameId = data.message;

            assert Minecraft.getInstance().player != null;

            UXHelper.sendSystemMessage(
                    String.format(
                            "[StealthPipe]: Join with the mod on another client using: %s.stealth.link", gameId
                    ),
                    ChatFormatting.GREEN
            );

            UXHelper.sendSystemMessage(
                    "StealthPipe is experimental! You may need to restart the client to join another world after leaving this one.",
                    ChatFormatting.RED
            );


            ModState.gameId.set(gameId);

            WebSocketHelper.connectToServer();

        }
    }

    @Inject(method="stopServer", at=@At("HEAD"))
    private void stopServer(CallbackInfo ci) {

        boolean wsConnected = ModState.webSocketOpen.get();

        if (wsConnected) {

            ModState.relayClient.get().close();

            LOGGER.info("Detected integrated server closed");

        }

    }


    @Inject(method="publishServer", at=@At("HEAD"))
    private void injectPublishServer(GameType gameType, boolean bl, int i, CallbackInfoReturnable<Boolean> cir) {

        System.out.println("Server now open to LAN! Connecting via websocket");


        new Thread(() -> {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(StealthPipe.config.RELAY_IP + "/create"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();

            ModState.gameOpenToLan.set(true);

            try {

                boolean isAvailable = pingRelay();
                getRoundTripLatency();
                if (!isAvailable) {
                    LOGGER.error("Could not connect to the relay: pinging failed");

                    UXHelper.sendSystemMessage("[StealthPipe]: Failed to reach the relay, connection failed", ChatFormatting.RED);

                    return;
                }

                establishConnection(request);

            } catch (Exception e) {
                System.out.printf("An error occurred while trying to create room ID: %s%n", e.toString());

                String stackTrace = StackTraceHelper.getStackTraceAsString(e);

                UXHelper.sendSystemMessage(
                        String.format("[StealthPipe]: An error occurred while trying to create room ID: %s%n%s", e.toString(), stackTrace),
                        ChatFormatting.RED
                );
            }
        }).start();






    }

}
