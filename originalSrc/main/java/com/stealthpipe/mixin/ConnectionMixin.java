package com.stealthpipe.mixin;

import com.stealthpipe.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(Connection.class)
@Environment(EnvType.CLIENT) // Run on INTEGRATED SERVER only, but not on DEDICATED SERVER
public abstract class ConnectionMixin {


    private Set<String> warnedIDs = new HashSet<>();

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    @Shadow
    @Final
    private PacketFlow receiving;


    @Shadow private Channel channel;



    @Inject(method = "configurePacketHandler", at = @At("RETURN"))
    private void injectRelay(ChannelPipeline pipeline, CallbackInfo ci) {



        System.out.println("Inject into network pipeline");

        boolean isClientSending = (this.receiving == PacketFlow.CLIENTBOUND);
        String label = isClientSending ? "CLIENT_OUT" : "SERVER_OUT";

        if (!isClientSending) {
            ModState.serverChannel.set(this.channel);
        }

        pipeline.addFirst( "stealth_relay_send", new StealthChannelOutboundHandlerAdapter(label));
    }


    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private static void injectConnect(InetSocketAddress inetSocketAddress, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {

        LOGGER.info("Attempting to connect");

        String host = inetSocketAddress.getHostString();

        if (host.endsWith(StealthPipe.config.CONNECTION_SUFFIX)) {

            // Connect to relay

            EventLoop eventLoop = eventLoopGroupHolder.eventLoopGroup().next();

            String gameId = host.substring(0, host.length() - StealthPipe.config.CONNECTION_SUFFIX.length());

            ModState.isClientConnectingToStealthServer.set(true);

            // Connect to stealth relay, if possible

            try {
                LOGGER.info("Attempting to connect to relay...");
                connectToStealthRelay(gameId);
            } catch (Exception e) {
                LOGGER.error("Failed to connect to relay: ", e);
                return;
            }

            // Create a fake channel


            DefaultEventLoop loop = new DefaultEventLoop();
            EmbeddedChannel fakeChannel = new EmbeddedChannel();

            ((ConnectionChannelAccessor) connection).setChannel(fakeChannel);

            setupPipeline(fakeChannel, connection);


            loop.register(fakeChannel);
            fakeChannel.pipeline().fireChannelRegistered();
            fakeChannel.pipeline().fireChannelActive();

            DefaultChannelPromise promise = new DefaultChannelPromise(fakeChannel, eventLoop);
            promise.setSuccess();


            cir.setReturnValue(promise);
            LOGGER.info("Channel injected to redirect to local fake channel");


            ModState.relayClientChannel.set(fakeChannel);


        }
    }

    @Unique
    private static void setupPipeline(Channel channel, Connection connection) {
        ChannelPipeline pipeline = channel.pipeline();

        pipeline.addLast("timeout", new io.netty.handler.timeout.ReadTimeoutHandler(30));
        pipeline.addFirst("stealth_relay_send_" + channel.id().asShortText(), new StealthChannelOutboundHandlerAdapter("CLIENT_OUT"));



        Connection.configureSerialization(pipeline, PacketFlow.CLIENTBOUND, false, null);
        connection.configurePacketHandler(pipeline); // This also configures writing injection, mixin above



    }

    @Unique
    private static void connectToStealthRelay(String gameId) throws Exception {

        ModState.gameId.set(gameId);

        WebSocketHelper.connectToServer();

        StealthWebSocketClient wsClient = ModState.relayClient.get();

        UUID clientUuid = UUID.randomUUID();
        String relayClientPacket = "CLIENTUUID_" + clientUuid.toString();

        ModState.relayClientUuid.set(clientUuid.toString());

        byte[] packetInBytes = relayClientPacket.getBytes(StandardCharsets.UTF_8);

        LOGGER.info("Sent initial relay client packet: {}", relayClientPacket);

        wsClient.send(packetInBytes);
    }

}
