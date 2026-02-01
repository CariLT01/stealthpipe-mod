package com.stealthpipe.mixin;

import com.stealthpipe.*;
import io.netty.channel.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

@Mixin(Connection.class)
@Environment(EnvType.CLIENT) // Run on INTEGRATED SERVER only, but not on DEDICATED SERVER
public abstract class ConnectionMixin {

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "configurePacketHandler", at = @At("RETURN"))
    private void injectRelay(ChannelPipeline pipeline, CallbackInfo ci) {
        ConnectionHelper.injectInPipeline(pipeline, this.receiving);
    }

    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private static void injectConnect(InetSocketAddress inetSocketAddress, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        ConnectionHelper.connectToRelay(inetSocketAddress, eventLoopGroupHolder.eventLoopGroup().next(), connection, cir);
    }


}
