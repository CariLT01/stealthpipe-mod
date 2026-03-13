package com.stealthpipe;

import io.netty.channel.Channel;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class StealthPipe implements ModInitializer {
	public static final String MOD_ID = "stealthpipe";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36";
	public static String REAL_MOD_VERSION = "6.0.1";
	public static String CONNECTION_SUFFIX = ".stealth.link";
	public static String MOD_VERSION = "4.0.0";
	public String PROTOCOL_VERSION = "6";

	public static ClientProxy CLIENT_PROXY;


	public static StealthPipeConfig config;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		config = StealthPipeConfig.load();

		LOGGER.info("Stealth initializing");

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ModState.minecraftServer.set(server);


			LOGGER.info("Registered Minecraft Server instance");
		});

		/* NOt commented code ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Make sure we fire packets in queue that are lingering
			if (!ModState.gameOpenToLan.get()) return;
			for (Map.Entry<Channel, GameConnectionWebSocket> entry : ModState.channelToWSClient.entrySet()) {
				entry.getValue().firePacketsInQueue();
			}
		}); */


	}
}