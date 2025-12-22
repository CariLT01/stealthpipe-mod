package com.stealthpipe.mixin.client;


import com.stealthpipe.Config;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.fabricmc.fabric.impl.command.client.ClientCommandInternals", remap = false)
public class FabricCommandDispatcherFix {
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.MOD_ID);

    @Shadow
    private static CommandDispatcher<Object> activeDispatcher;

    @Inject(method = "getActiveDispatcher", at = @At("HEAD"), cancellable = true)
    private static void fixNullDispatcher(CallbackInfoReturnable<CommandDispatcher<FabricClientCommandSource>> cir) {
        if (activeDispatcher == null) {
            LOGGER.warn("Returned empty command dispatcher, client-side commands might not work");

            // Return an empty dispatcher instead of crashing
            cir.setReturnValue(new CommandDispatcher<>());
        }
    }
}
