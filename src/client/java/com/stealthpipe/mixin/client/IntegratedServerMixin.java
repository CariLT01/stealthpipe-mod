package com.stealthpipe.mixin.client;

import com.google.gson.Gson;
import com.stealthpipe.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Unique
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    @Inject(method="publishServer", at=@At("HEAD"))
    private void injectPublishServer(GameType gameType, boolean bl, int i, CallbackInfoReturnable<Boolean> cir) {

        System.out.println("Server now open to LAN! Connecting via websocket");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.RELAY_IP + "/create"))
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();

        ModState.gameOpenToLan.set(true);

        try {
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

        } catch (Exception e) {
            System.out.printf("An error occurred while trying to create room ID: %s%n", e.toString());

            String stackTrace = StackTraceHelper.getStackTraceAsString(e);

            UXHelper.sendSystemMessage(
                    String.format("[STEALTH]: An error occurred while trying to create room ID: %s%n%s", e.toString(), stackTrace),
                    ChatFormatting.RED
            );
        }




    }

}
