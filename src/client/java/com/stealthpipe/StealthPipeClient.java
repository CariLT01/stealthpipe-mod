package com.stealthpipe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {


			LOGGER.info("Detected disconnect via Fabric EVENT");

			StealthWebSocketClient wsClient = ModState.relayClient.get();

			if (wsClient != null) {
				wsClient.close();
			}

			ModState.resetState();

		});
	}
}