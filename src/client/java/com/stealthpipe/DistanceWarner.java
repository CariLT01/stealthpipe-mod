package com.stealthpipe;

import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DistanceWarner {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    private static final double SERVER_LONGITUDE = -77.4875;
    private static final double SERVER_LATITUDE = 39.0437;

    private static void getAndWarn() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://free.freeipapi.com/api/json"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("User-Agent", StealthPipe.USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("IP Geolocation GET request failed with status code: " + response.statusCode());
        }

        IpGelocationJsonResult data = GSON.fromJson(response.body(), IpGelocationJsonResult.class);

        double distance = calculateDistance(data.latitude, data.longitude, SERVER_LATITUDE, SERVER_LONGITUDE);
        LOGGER.info("Distance from relay is: {} km (based on approximate IP geolocation, calculated entirely on the client)", distance);

        if (distance > 4000) {
            UXHelper.sendStealthPipeSystemMessage(
                    String.format(
                            "§eNotice: §6You are %.0f km away from the relay.\nExcept high ping. Host your own relay at a location closer to you for less lag! (Instructions on the Modrinth page).\n§8The distance was calculated entirely on the client based on the approximate location indicated by your IP address. No data was sent to StealthPipe's services or relays.",
                            distance
                    )
            );
            UXHelper.sendSystemMessageComponent(
                    Component.literal("Hosting Instructions: ").append(
                            Component.literal("Open").withStyle(style -> style.withClickEvent(
                                    new ClickEvent.OpenUrl(URI.create("https://github.com/CariLT01/stealthpipe-relay/blob/main/HOSTING.md"))
                            ))
                    ),
                    ChatFormatting.GRAY
            );
        }
    }

    public static void warnDistance() {
        // Calculate distance based on IP geo-location

        try {
            getAndWarn();
        } catch (Exception e) {
            LOGGER.error("Failed to get IP geolocation to warn about large distances: ", e);
        }
    }
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371; // Earth's radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
