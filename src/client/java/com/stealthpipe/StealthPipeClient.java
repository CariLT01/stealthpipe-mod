package com.stealthpipe;

import com.stealthpipe.connection.game.GameConnectionInterface;
import com.stealthpipe.connection.signal.SignalWebSocket;
import com.stealthpipe.enums.ConnectionDisconnectReason;
import com.stealthpipe.ui.ConnectionStatusInterface;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StealthPipeClient implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

	private static void renderOverlay() {

	}

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		StealthPipe.CLIENT_PROXY = new ClientProxyImpl();

		ModState.clientThreadExecutor.set(Minecraft.getInstance());

		LOGGER.info("Client initialized stealth");

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			/*? if <=1.21.11 {*/
			/*ScreenEvents.afterRender(screen).register((scr, guiGraphics, mouseX, mouseY, tickDelta) -> {
				ConnectionStatusInterface.renderConnectionStatusText(guiGraphics);
			});
			*//*?} else if >=26.1 {*/
			ScreenEvents.afterExtract(screen).register((scr, gui, a, b, c) -> {
				ConnectionStatusInterface.renderConnectionStatusText(gui);
			});
			/*?} else {*/

			/*? } */
		});

		/*? if <= 1.21.11 {*/
		/*HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
			ConnectionStatusInterface.renderConnectionStatusText(guiGraphics);
		});
		*//*?} else {*/
		HudElement myElement = (graphics, tracker) -> {
			ConnectionStatusInterface.renderConnectionStatusText(graphics);
		};
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(StealthPipe.MOD_ID, "connection_overlay"), myElement);
		/*?} */
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