package com.stealthpipe.mixin;

import com.stealthpipe.StealthPipe;
import com.stealthpipe.interfaces.IConnectionInjector;
import com.stealthpipe.connection.adapters.StealthChannelOutboundHandlerAdapter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.net.InetSocketAddress;
import java.util.List;

@Mixin(ServerConnectionListener.class)
public class ServerConnectionListenerMixin implements IConnectionInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    @Shadow
    @Final
    private MinecraftServer server;
    @Shadow
    @Final
    private List<Connection> connections;

    @Unique
    public void injectVirtualConnection(EmbeddedChannel virtualChannel) {

        int rateLimit = this.server.getRateLimitPacketsPerSecond();
        Connection connection = (rateLimit > 0)
                ? new RateKickingConnection(rateLimit)
                : new Connection(PacketFlow.SERVERBOUND);

        // 2. Attach the virtual channel to the connection

        ConnectionChannelAccessor accessor = (ConnectionChannelAccessor) connection;

        accessor.setChannel(virtualChannel);
        accessor.setAddress(
                new InetSocketAddress("127.0.0.1", 25565)
        );

        // 3. Configure the pipeline for the virtual channel
        ChannelPipeline pipeline = virtualChannel.pipeline();

        // Add the timeout and serialization (Mirroring vanilla initChannel)
        pipeline.addLast("timeout", new ReadTimeoutHandler(30));
        Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);

        pipeline.addFirst("stealth_relay_send_" + virtualChannel.id().asShortText(), new StealthChannelOutboundHandlerAdapter("SERVER_OUT"));

        // 4. Register the connection in the server's primary list
        synchronized (this.connections) {
            this.connections.add(connection);
        }

        // 5. Set up the packet handler and the initial Handshake listener
        connection.configurePacketHandler(pipeline);
        connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(this.server, connection));



        LOGGER.info("virtual channel injected");
    }

}
