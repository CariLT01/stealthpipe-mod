package com.stealthpipe;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StealthPipe implements ModInitializer {
	public static final String MOD_ID = "stealthpipe";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Stealth initializing");

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ModState.minecraftServer.set(server);


			LOGGER.info("Registered Minecraft Server instance");
		});


	}
}