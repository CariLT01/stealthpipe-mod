package com.stealthpipe.mixin.client;

import com.stealthpipe.StealthPipe;
import com.stealthpipe.StealthPipeConfig;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {

    @Inject(method = "resolveAddress", at=@At("HEAD"), cancellable = true)
    public void resolveAddress(ServerAddress serverAddress, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        if (serverAddress.getHost().endsWith(StealthPipe.config.CONNECTION_SUFFIX)) {

            // Make it try to connect to it
            Optional<ResolvedServerAddress> optional = Optional.of(
                    ResolvedServerAddress.from(
                            new InetSocketAddress(serverAddress.getHost(), 25565)
                    )
            );

            cir.setReturnValue(optional);
        }



    }
}
