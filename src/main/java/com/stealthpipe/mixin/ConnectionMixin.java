package com.stealthpipe.mixin;

import com.stealthpipe.*;
import com.stealthpipe.connection.ConnectionHelper;
import io.netty.channel.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;



//? if =1.21.11
//import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

//? if <=1.21.10
//import static net.minecraft.network.Connection.NETWORK_EPOLL_WORKER_GROUP;
//? if <=1.21.10
//import static net.minecraft.network.Connection.NETWORK_WORKER_GROUP;

@Mixin(Connection.class)
@Environment(EnvType.CLIENT) // Run on INTEGRATED SERVER only, but not on DEDICATED SERVER
public abstract class ConnectionMixin {

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "configurePacketHandler", at = @At("RETURN"))
    private void injectRelay(ChannelPipeline pipeline, CallbackInfo ci) {
        if (ModState.isStealthPipeConnection.get()) {
            ConnectionHelper.injectInPipeline(pipeline, this.receiving);
        }
    }

    /*? if =1.21.11 { */
    /*@Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    public static void connect(InetSocketAddress inetSocketAddress, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        if (ModState.isStealthPipeConnection.get()) {
            ConnectionHelper.connectToRelay(inetSocketAddress, eventLoopGroupHolder.eventLoopGroup().next(), connection, cir);
        }
    }
    *//*? } else if =26.1 { */
    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    public static void connect(InetSocketAddress inetSocketAddress, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        if (ModState.isStealthPipeConnection.get()) {
            ConnectionHelper.connectToRelay(inetSocketAddress, eventLoopGroupHolder.eventLoopGroup().next(), connection, cir);
        }
    }

    /*? } else { */
    /*@Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private static void injectConnect2(InetSocketAddress inetSocketAddress, boolean bl, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        io.netty.channel.EventLoopGroup group;
        if (io.netty.channel.epoll.Epoll.isAvailable() && bl) {
            group = (io.netty.channel.EventLoopGroup) NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            group = (io.netty.channel.EventLoopGroup) NETWORK_WORKER_GROUP.get();
        }

        ConnectionHelper.connectToRelay(inetSocketAddress, group.next(), connection, cir);
    }
    *//*? } */



}
