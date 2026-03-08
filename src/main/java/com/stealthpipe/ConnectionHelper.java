package com.stealthpipe;

import com.stealthpipe.mixin.ConnectionChannelAccessor;
import dev.onvoid.webrtc.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class ConnectionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    public static void tryEstablishRTC(String gameId) {



    }

    public static void connectToRelay(InetSocketAddress inetSocketAddress, EventLoop eventLoop, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        LOGGER.info("Attempting to connect");

        String host = inetSocketAddress.getHostString();

        if (host.endsWith(StealthPipe.CONNECTION_SUFFIX)) {

            // Connect to relay

            String gameId = host.substring(0, host.length() - StealthPipe.CONNECTION_SUFFIX.length());

            ModState.isClientConnectingToStealthServer.set(true);

            // Create a fake channel


            DefaultEventLoop loop = new DefaultEventLoop();
            EmbeddedChannel fakeChannel = new EmbeddedChannel();

            ((ConnectionChannelAccessor) connection).setChannel(fakeChannel);

            setupPipeline(fakeChannel, connection);


            loop.register(fakeChannel);
            fakeChannel.pipeline().fireChannelRegistered();
            fakeChannel.pipeline().fireChannelActive();

            // Connect to stealth relay, if possible

            try {
                LOGGER.info("Attempting to connect to relay...");
                connectToStealthRelay(gameId, fakeChannel);
            } catch (Exception e) {
                LOGGER.error("Failed to connect to relay: ", e);
                return;
            }



            DefaultChannelPromise promise = new DefaultChannelPromise(fakeChannel, eventLoop);
            promise.setSuccess();


            cir.setReturnValue(promise);
            LOGGER.info("Channel injected to redirect to local fake channel");


            ModState.relayClientChannel.set(fakeChannel);

        }
    }

    private static void setupPipeline(Channel channel, Connection connection) {

        LOGGER.info("Setting up pipeline");

        ChannelPipeline pipeline = channel.pipeline();

        LOGGER.info("Inject timeout");

        pipeline.addLast("timeout", new io.netty.handler.timeout.ReadTimeoutHandler(30));

        LOGGER.info("Inject adapter");

        // pipeline.addFirst("stealth_relay_send_" + channel.id().asShortText(), new StealthChannelOutboundHandlerAdapter("CLIENT_OUT"));



        Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND, false, null);
        connection.configurePacketHandler(pipeline); // This also configures writing injection, mixin above

        LOGGER.info("Finished pipeline setup");

    }

    private static void connectToStealthRelay(String gameId, Channel gameChannel) throws Exception {

        LOGGER.info("Connect to stealth relay");

        try {
            WebRTCClient rtcClient = new WebRTCClient((byte[] msg) -> {
                ModState.clientThreadExecutor.get().execute(() -> {
                    gameChannel.pipeline().fireChannelRead(Unpooled.wrappedBuffer(msg));
                });
            });

            rtcClient.tryEstablishRTC(gameId);

            ModState.relayRTCClient.set(rtcClient);
            ModState.usingWebRTC.set(true);
            LOGGER.info("Successfully established a direct WebRTC connection");
        } catch (Exception e) {
            LOGGER.error("Failed to establish direct P2P WebRTC, falling back to WSS-relay based", e);

            StealthWebSocketClient wsClient = new StealthWebSocketClient(URI.create(StealthPipe.config.RELAY_IP.replace("http://", "ws://").replace("https://", "wss://") + "/join?id=" + gameId + "&version=" + StealthPipe.MOD_VERSION),WebsocketClientType.CLIENT_TO_RELAY, gameChannel, gameId);
            wsClient.connect();

            ModState.relayClient.set(wsClient);
            ModState.usingWebRTC.set(false);

            LOGGER.info("Established a WebSocket relay based connection");
        }

    }

    public static void injectInPipeline(ChannelPipeline pipeline, PacketFlow receiving) {
        LOGGER.info("Configure network pipeline");

        boolean isClientSending = (receiving == PacketFlow.CLIENTBOUND);
        String label = isClientSending ? "CLIENT_OUT" : "SERVER_OUT";

        LOGGER.info("Injecting adapter");

        pipeline.addFirst( "stealth_relay_send", new StealthChannelOutboundHandlerAdapter(label));
    }

    public static void handleDisconnectEvent() {
        LOGGER.info("Detected disconnect via Fabric EVENT");

        StealthWebSocketClient wsClient = ModState.relayClient.get();

        if (wsClient != null) {
            wsClient.close();
        }

        ModState.resetState();
    }
}
