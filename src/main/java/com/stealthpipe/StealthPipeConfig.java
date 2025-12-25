package com.stealthpipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class StealthPipeConfig {

    public String RELAY_IP = DefaultConfigValues.RELAY_IP;
    public int RELAY_PING_ATTEMPTS = DefaultConfigValues.RELAY_PING_ATTEMPTS;
    // public static final String RELAY_IP = "http://127.0.0.1:7860";
    // public static final String RELAY_IP_WS = "ws://127.0.0.1:7860";

    public String CONNECTION_SUFFIX = ".stealth.link";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("stealthpipe.json");

    public static StealthPipeConfig load() {
        if (!Files.exists(PATH)) return new StealthPipeConfig();
        try (Reader reader = Files.newBufferedReader(PATH)) {
            return GSON.fromJson(reader, StealthPipeConfig.class);
        } catch (Exception e) {
            return new StealthPipeConfig();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(this, writer);
        } catch (Exception ignored) {}
    }

}
