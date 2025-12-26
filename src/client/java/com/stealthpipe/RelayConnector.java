package com.stealthpipe;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Unique;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

public class RelayConnector {

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

                if (response.statusCode() == 200 && !Objects.equals(response.body(), "OK")) {
                    throw new RuntimeException("Relay did not return string 'OK', received: " + response.body());
                } else {

                    if (response.statusCode() == 200) {
                        return true;
                    } else {
                        UXHelper.sendSystemMessage(
                                String.format("[StealthPipe]: Relay is in an unhealthy state and is unavailable to serve your request. Message: %s%nPlease try again later.", response.body()),
                                ChatFormatting.RED
                        );

                        return false;
                    }
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
        UXHelper.sendSystemMessageComponent(
                Component.literal("To host a relay for higher performance and reduced ping, see: ").append(
                        Component.literal("https://github.com/CariLT01/stealthpipe-relay/blob/main/HOSTING.md").withStyle(style -> style.withClickEvent(
                                new ClickEvent.OpenUrl(URI.create("https://github.com/CariLT01/stealthpipe-relay/blob/main/HOSTING.md"))
                        ))
                ),
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

            int pingMs = ((int) (end - begin) ) * 2;

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




    @Unique
    private static boolean checkPoW(String salt, String nonce, int difficulty) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + nonce).getBytes(StandardCharsets.UTF_8));

            int fullBytesToCheck = difficulty / 2;

            for (int i = 0; i < fullBytesToCheck; i++) {
                if (hash[i] != 0) return false;
            }

            if (difficulty % 2 != 0) {
                // The byte at index 'fullBytesToCheck' must have its first 4 bits as 0.
                // This means the byte value must be between 0 and 15 (0x0F).
                return (hash[fullBytesToCheck] & 0xFF) <= 0x0F;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Unique
    private ProofOfWorkChallengeResult doProofOfWorkChallenge() throws Exception {

        UXHelper.sendSystemMessage(
                "[StealthPipe]: Authenticating client...",
                ChatFormatting.GRAY
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StealthPipe.config.RELAY_IP + "/pow"))
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assert response.statusCode() == 200;

        ProofOfWorkChallengePayload data = GSON.fromJson(response.body(), ProofOfWorkChallengePayload.class);

        DecodedJWT jwt = JWT.decode(data.token);
        String salt = jwt.getClaim("salt").asString();
        int difficulty = jwt.getClaim("diff").asInt();

        // Warn user about high difficulty and slow authentication
        switch (difficulty) {
            case 6:
                UXHelper.sendSystemMessage(
                        "The relay has issued a challenge with a higher difficulty than usual to throttle traffic and protect itself from potential ongoing attacks. This will make authenticating slower. Please wait...",
                        ChatFormatting.GRAY
                );
                break;

            case 7:
                UXHelper.sendSystemMessage(
                        "The relay has issued a challenge with a very high difficulty than usual to throttle traffic and protect itself from the ongoing attacks. This will make authentication take a lot longer than usual. Please hold...",
                        ChatFormatting.GRAY
                );
                break;

            default:
                break;
        }

        LOGGER.info("Doing proof of work challenge. Salt: {} Difficulty: {}", salt, difficulty);

        int nonce = 0;
        while (true) {

            boolean valid = checkPoW(salt, Integer.toString(nonce), difficulty);

            if (valid) break;

            nonce++;
        }

        LOGGER.info("Proof of work nonce: {}", nonce);

        return new ProofOfWorkChallengeResult(data.token, nonce);

    }

    public void connectToRelay() {
        new Thread(() -> {



            ModState.gameOpenToLan.set(true);

            try {

                boolean isAvailable = pingRelay();

                if (!isAvailable) {
                    LOGGER.error("Could not connect to the relay: pinging failed");

                    UXHelper.sendSystemMessage("[StealthPipe]: Failed to reach the relay, connection failed", ChatFormatting.RED);
                    UXHelper.sendSystemMessage("[StealthPipe]: Alternative relays are available in the mod's description, feel free to try them. And remember, you can always host your own :D You can change the Relay IP in the mod's config menu.", ChatFormatting.WHITE);
                    UXHelper.sendSystemMessageComponent(
                            Component.literal("https://github.com/CariLT01/stealthpipe-mod/blob/main/ALTERNATIVE_RELAYS.md")
                                    .withStyle(style -> style.withClickEvent(
                                            new ClickEvent.OpenUrl(URI.create("https://github.com/CariLT01/stealthpipe-mod/blob/main/ALTERNATIVE_RELAYS.md"))
                                    )),
                            ChatFormatting.WHITE
                    );

                    return;
                } else {
                    UXHelper.sendSystemMessage(
                            "[StealthPipe]: Found connection to reach relay",
                            ChatFormatting.GREEN
                    );
                }

                ProofOfWorkChallengeResult powResult = doProofOfWorkChallenge();
                getRoundTripLatency();



                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(StealthPipe.config.RELAY_IP + String.format("/create?token=%s&nonce=%s", powResult.token(), powResult.nonce())))
                        .version(HttpClient.Version.HTTP_1_1)
                        .GET()
                        .build();




                UXHelper.sendSystemMessage(
                        "[StealthPipe]: Creating WebSocket connection... (if this takes too long, server might be in a deadlock!)",
                        ChatFormatting.GRAY
                );

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
