package com.stealthpipe;

import com.stealthpipe.connection.game.GameConnectionInterface;
import com.stealthpipe.connection.signal.SignalWebSocket;
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

		StealthPipe.CLIENT_PROXY = new ClientProxyImpl();

		ModState.clientThreadExecutor.set(Minecraft.getInstance());

		LOGGER.info("Client initialized stealth");

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {


			LOGGER.info("Detected disconnect via Fabric EVENT");

			GameConnectionInterface wsClient = ModState.relayClient.get();

			if (wsClient != null) {
				wsClient.disconnectWithReason(ConnectionDisconnectReason.FabricEventDisconnectClient);
			}

			SignalWebSocket wsClient2 = ModState.signalClient.get();
			if (wsClient2 != null) {
				wsClient2.disconnectWithReason(ConnectionDisconnectReason.FabricEventDisconnectClient);
			}

			ModState.resetState();

		});

		/* NOT COMMENTED CODE ClientTickEvents.END_CLIENT_TICK.register((client) -> {
			// Fire lingering packets in queue
			if (ModState.relayClient.get() != null && ModState.webSocketOpen.get()) {
				ModState.relayClient.get().firePacketsInQueue();
			}
		}); */
	}
}