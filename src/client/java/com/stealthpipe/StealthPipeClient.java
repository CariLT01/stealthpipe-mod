package com.stealthpipe;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StealthPipeClient implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.


		ModState.clientThreadExecutor.set(Minecraft.getInstance());

		LOGGER.info("Client initialized stealth");
	}
}