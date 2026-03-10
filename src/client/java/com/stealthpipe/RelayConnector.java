package com.stealthpipe;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Unique;

import java.io.File;
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

        UXHelper.sendStealthPipeSystemMessage("Connecting to the relay...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StealthPipe.config.RELAY_IP + "/ping?version=" + StealthPipe.MOD_VERSION))
                .version(HttpClient.Version.HTTP_1_1)
                .header("User-Agent", StealthPipe.USER_AGENT)
                .GET()
                .build();



        for (int i = 0; i < StealthPipe.config.RELAY_PING_ATTEMPTS; i++) {

            try {

                if (i != 0) {

                    int retryDelay = Math.min(30, (int) Math.pow(2, i + 1));
                    UXHelper.sendStealthPipeSystemMessage(
                            String.format("Retrying in %s seconds...", (retryDelay))
                    );
                    Thread.sleep(retryDelay * 1000L);
                    UXHelper.sendStealthPipeSystemMessage(
                            String.format("Attempt %s...", (i + 1))
                    );
                }

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && !Objects.equals(response.body(), "OK")) {
                    throw new RuntimeException("Relay did not return string 'OK', received: " + response.body());
                } else {

                    if (response.statusCode() == 200) {
                        return true;
                    } else {
                        UXHelper.sendStealthPipeSystemMessage(
                                String.format("§cRelay is in an unhealthy state and is unavailable to serve your request. Message: %s%nPlease try again later.", response.body())
                        );

                        return false;
                    }
                }

            } catch (Exception e) {

                LOGGER.error("An error occurred while trying to reach relay: ", e);

                UXHelper.sendStealthPipeSystemMessage(
                        "§cFailed to reach the relay. Check the logs for more info."
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

        /* try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(StealthPipe.config.RELAY_IP + "/ping"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("User-Agent", StealthPipe.USER_AGENT)
                    .GET()
                    .build();

            long begin = Instant.now().toEpochMilli();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long end = Instant.now().toEpochMilli();

            int pingMs = ((int) (end - begin) ) * 2;

            LOGGER.info("Ping: {}ms", pingMs);

            UXHelper.sendStealthPipeSystemMessage(
                    String.format("Ping is at (%sms).", pingMs)
            );


        } catch (Exception e) {
            LOGGER.error("Failed to measure round-trip latency: ", e);
        } */


    }

    @Unique
    private void establishConnection(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assert response.statusCode() == 200;

        System.out.printf("Field data is: %s%n", response.body());

        assert !response.body().isEmpty();

        ResponseModel data;
        try {
            data = GSON.fromJson(response.body(), ResponseModel.class);
        } catch (Exception e) {
            throw new Exception("Server returned unexpected response: " + response.body());
        }


        if (data == null) {
            throw new IllegalArgumentException("Server returned unexpected response: " + response.body());
        }

        if (data.ok) {
            String gameId = data.message;
            if (data.reuseToken != null && !Objects.equals(data.reuseToken, "")) {
                LOGGER.info("Set reuse token to: {}", data.reuseToken);
                ModState.reuseToken.set(data.reuseToken);
            }


            assert Minecraft.getInstance().player != null;



            UXHelper.sendStealthPipeSystemMessage(
                    "§cCaution: You are running the latest experimental build of StealthPipe (v6.0.0-alpha.2-WebRTC). Expect bugs, issues, and glitches!\n§8Download a stable build (version 5 and under) for a smoother experience. Version 6 clients are not compatible with version 5 clients.\n§6Note: you will be disconnected if your room is idle for more than 15 minutes."
            );

            /* if (!StealthPipe.config.HAS_SHOWN_WEBRTC_PRIVACY_NOTE) {
                UXHelper.sendStealthPipeSystemMessage(
                        "§9Important Privacy Note: §8StealthPipe may use §fWebRTC Technology §8to create faster, direct connections between players joining you. This requires sharing your public IP and may reveal your real IP even with a VPN.\n\n§8Disable by setting §f\"Host Allow WebRTC\" §8to §cfalse §8in the config (§8may significantly increase latency for players connecting to you§8).\n\n§8This message will not appear again."
                );
                StealthPipe.config.HAS_SHOWN_WEBRTC_PRIVACY_NOTE = true;
                StealthPipe.config.save();
            } */



            if (Objects.equals(gameId, "676767")) {
                UXHelper.sendStealthPipeSystemMessage(
                        "§cWarning: §6Room privacy cannot be guaranteed for this session, and you will lose this room code if your internet connection disconnects. Restart the session to fix this."
                );
            }

            UXHelper.sendStealthPipeSystemMessage("Join with the mod on another client using: ");
            UXHelper.sendSystemMessageComponent(Component.literal(gameId + ".stealth.link").withStyle(style -> style
                            .withUnderlined(true)
                            .withColor(ChatFormatting.LIGHT_PURPLE)
                            .withClickEvent(
                                    new ClickEvent.CopyToClipboard(gameId + ".stealth.link")
                            )
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy to clipboard")))),
                    ChatFormatting.WHITE);

            // DistanceWarner.warnDistance();

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

        UXHelper.sendStealthPipeSystemMessage(
                "Authenticating client..."
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StealthPipe.config.RELAY_IP + "/pow"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("User-Agent", StealthPipe.USER_AGENT)
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
                UXHelper.sendStealthPipeSystemMessage(
                        "The relay has issued a challenge with a higher difficulty than usual to throttle traffic and protect itself from potential ongoing attacks. This will make authenticating slower. Please wait..."
                );
                break;

            case 7:
                UXHelper.sendStealthPipeSystemMessage(
                        "The relay has issued a challenge with a very high difficulty than usual to throttle traffic and protect itself from the ongoing attacks. This will make authentication take a lot longer than usual. Please hold..."
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

                    File logFile = new File(Minecraft.getInstance().gameDirectory, "logs/latest.log");

                    LOGGER.error("Could not connect to the relay: pinging failed");

                    UXHelper.sendStealthPipeSystemMessage("§cFailed to reach the relay, connection failed");
                    UXHelper.sendStealthPipeSystemMessage("§r--- §nTroubleshooting§r --");
                    UXHelper.sendStealthPipeSystemMessage("Please try the following:");
                    UXHelper.sendStealthPipeSystemMessage(" - Try starting a new session again");
                    UXHelper.sendStealthPipeSystemMessage(" - Try using a different Java Runtime at a different installation location");
                    UXHelper.sendStealthPipeSystemMessage(" - Check your antivirus or firewall settings");
                    UXHelper.sendStealthPipeSystemMessage(" - Try connecting to an alternative relay");
                    UXHelper.sendStealthPipeSystemMessage(" - Try restarting your computer");
                    UXHelper.sendSystemMessageComponent(
                            Component.literal("§a§nOpen Alternative Relays List")
                                    .withStyle(style -> style.withClickEvent(
                                            new ClickEvent.OpenUrl(URI.create("https://github.com/CariLT01/stealthpipe-mod/blob/main/ALTERNATIVE_RELAYS.md"))
                                    )),
                            ChatFormatting.WHITE
                    );
                    UXHelper.sendStealthPipeSystemMessage("To help us fix the issue, please attach your Minecraft log file (latest.log) when reporting a problem.");
                    UXHelper.sendSystemMessageComponent(
                            Component.literal("§a§nOpen latest.log")
                                    .withStyle(style -> style.withClickEvent(
                                            new ClickEvent.OpenFile(logFile)
                                    )),
                            ChatFormatting.WHITE
                    );


                    return;
                }

                ProofOfWorkChallengeResult powResult = doProofOfWorkChallenge();
                getRoundTripLatency();



                String reuseTokenParam = (ModState.reuseToken.get() != null && !Objects.equals(ModState.reuseToken.get(), ""))? "&reuseToken=" + ModState.reuseToken.get() : "";

                LOGGER.info("Reuse token param: {}", reuseTokenParam);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(StealthPipe.config.RELAY_IP + String.format("/create?token=%s&nonce=%s%s", powResult.token(), powResult.nonce(), reuseTokenParam)))
                        .version(HttpClient.Version.HTTP_1_1)
                        .header("User-Agent", StealthPipe.USER_AGENT)
                        .GET()
                        .build();

                UXHelper.sendStealthPipeSystemMessage(
                        "Creating WebSocket connection..."
                );

                establishConnection(request);

            } catch (Exception e) {
                System.out.printf("An error occurred while trying to create room ID: %s%n", e.getMessage());

                Minecraft.getInstance().execute(() -> {
                    UXHelper.sendStealthPipeSystemMessage(
                            String.format("§cAn error occurred while trying to create room ID: %s", e.getMessage())
                    );
                });

            }
        }).start();
    }
}
