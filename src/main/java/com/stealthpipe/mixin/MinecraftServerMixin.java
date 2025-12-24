package com.stealthpipe.mixin;


import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    // Disable onlineMode to prevent issues in development mode
    // Only applies to Integrated LAN servers; public server authentication is unaffected

    @Shadow
    private boolean onlineMode;

    @Inject(method="setUsesAuthentication", at=@At("HEAD"), cancellable = true)
    public void forceOfflineMode(boolean bl, CallbackInfo ci) {
        // Force set use authentication to false
        this.onlineMode = false;

        ci.cancel();
    }

    @Inject(method="usesAuthentication", at=@At("HEAD"), cancellable = true)
    public void forceOfflineMode2(CallbackInfoReturnable<Boolean> cir){
        cir.setReturnValue(false);
    }
}
