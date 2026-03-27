package com.stealthpipe.connection;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.stealthpipe.ModState;
import com.stealthpipe.StealthPipe;
import com.stealthpipe.other.UXHelper;
import com.stealthpipe.models.ProofOfWorkChallengePayload;
import com.stealthpipe.models.ProofOfWorkChallengeResult;
import com.stealthpipe.responses.ResponseModel;
import com.stealthpipe.ui.ConnectionStatusInterface;
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
import java.util.Objects;

public class HostRelayConnector {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    /**
     * Initial stage of the protocol. Pings the relay and checks for health (or version mismatch).
     * This allows the client to know if the relay is reachable before attempting the rest of the protocol.
     *
     * @return If the relay is reachable, healthy, and available to serve the request, it will return true. Otherwise, false will be returned.
     */

    @Unique
    private boolean pingRelay() {

        UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.connecting"));

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
                    UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.retrying", retryDelay));
                    Thread.sleep(retryDelay * 1000L);
                    UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.attempting", i + 1));
                }

                if (!ModState.gameOpenToLan.get()) {
                    LOGGER.warn("Attempt cancelled: game is no longer open to LAN");
                    return false;
                }

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && !Objects.equals(response.body(), "OK")) {
                    throw new RuntimeException("Relay did not return string 'OK', received: " + response.body());
                } else {

                    if (response.statusCode() == 200) {
                        return true;
                    } else {
                        UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.relayUnavailable", response.body()));

                        return false;
                    }
                }

            } catch (Exception e) {

                LOGGER.error("An error occurred while trying to reach relay: ", e);

                /* UXHelper.sendStealthPipeSystemMessage(
                        "§cFailed to reach the relay. Check the logs for more info."
                ); */

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

    /**
     * Creates the room and calls the function to establish the room's SIGNAL connection.
     * It also logs the room code in the chat for the user.
     *
     * @param request The initial request for the room creation HTTP request
     * @throws Exception Throws an exception if any part of the room establishment process fails
     */

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

            ModState.gameId.set(gameId);
            ConnectionStatusInterface.setConnectionStatusText(Component.translatable("status.stealthpipe.signalJoin"), 1);
            WebSocketHelper.connectToServer();

            UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.betaNotice"));

            if (!StealthPipe.config.HAS_SHOWN_WEBRTC_PRIVACY_NOTE) {
                UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.wrtcPrivacyNote"));
                StealthPipe.config.HAS_SHOWN_WEBRTC_PRIVACY_NOTE = true;
                StealthPipe.config.save();
            }



            if (Objects.equals(gameId, "676767")) {
                UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.roomPrivacyNote"));
            }

            UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.joinLink"));
            UXHelper.sendSystemMessageComponent(Component.literal(gameId + ".stealth.link").withStyle(style -> style
                            .withUnderlined(true)
                            .withColor(ChatFormatting.LIGHT_PURPLE)
                            .withClickEvent(
                                    new ClickEvent.CopyToClipboard(gameId + ".stealth.link")
                            )
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy to clipboard")))),
                    ChatFormatting.WHITE);

            // DistanceWarner.warnDistance();

            ConnectionStatusInterface.setConnectionStatusLength(0);





        }
    }


    /**
     * Checks if a proof of work is valid.
     *
     * @param salt The salt given by the server
     * @param nonce The nonce we want to check against with
     * @param difficulty The difficulty given by the server (number of zeroes)
     * @return If the proof of work is valid
     */

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

    /**
     * Queries the server for a proof of work challenge and solves it. The challenge is a tiny
     * SHA-256 puzzle. Yields.
     *
     * @return Returns the result that includes the nonce.
     * @throws Exception Throws an exception if any part of the protocol fails.
     */

    @Unique
    private ProofOfWorkChallengeResult doProofOfWorkChallenge() throws Exception {

        ConnectionStatusInterface.setConnectionStatusText(Component.translatable("status.stealthpipe.solvingPow"), 1);

        UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.authenticating"));

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
                UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.longerAuthenticationNotice1"));
                break;

            case 7:
                UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.longerAuthenticationNotice2"));
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

    /**
     * The main function that handles the full Host to Relay protocol to create a room and maintain a SIGNAL connection.
     * Does not throw any exceptions; all of them are caught inside and shown using chat messages and logged.
     */

    public void connectToRelay() {
        new Thread(() -> {



            ModState.gameOpenToLan.set(true);

            try {

                ConnectionStatusInterface.setConnectionStatusText(Component.translatable("status.stealthpipe.creatingRoom"), 0);
                ConnectionStatusInterface.setConnectionStatusText(Component.translatable("status.stealthpipe.findingRelay"), 1);

                boolean isAvailable = pingRelay();

                if (!isAvailable) {

                    File logFile = new File(Minecraft.getInstance().gameDirectory, "logs/latest.log");

                    LOGGER.error("Could not connect to the relay: pinging failed");

                    UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.connectionFailed"));
                    UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.troubleshooting"));
                    UXHelper.sendSystemMessageComponent(
                            Component.translatable("text.stealthpipe.alternativeRelaysList.open")
                                    .withStyle(style -> style.withClickEvent(
                                            new ClickEvent.OpenUrl(URI.create("https://github.com/CariLT01/stealthpipe-mod/blob/main/ALTERNATIVE_RELAYS.md"))
                                    )),
                            ChatFormatting.WHITE
                    );
                    UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.issueReport"));
                    UXHelper.sendSystemMessageComponent(
                            Component.translatable("text.stealthpipe.logsOpen")
                                    .withStyle(style -> style.withClickEvent(
                                            new ClickEvent.OpenFile(logFile)
                                    )),
                            ChatFormatting.WHITE
                    );

                    ConnectionStatusInterface.setConnectionStatusLength(0);

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

                ConnectionStatusInterface.setConnectionStatusText(Component.translatable("status.stealthpipe.establishingSignal"), 1);

                UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.creatingWebsocket"));

                establishConnection(request);

            } catch (Exception e) {
                ConnectionStatusInterface.setConnectionStatusLength(0);
                System.out.printf("An error occurred while trying to create room ID: %s%n", e.getMessage());

                Minecraft.getInstance().execute(() -> {
                    UXHelper.sendStealthPipeSystemMessage(Component.translatable("text.stealthpipe.roomCreationFailed", e.getMessage()));
                });

            }
        }).start();
    }
}
