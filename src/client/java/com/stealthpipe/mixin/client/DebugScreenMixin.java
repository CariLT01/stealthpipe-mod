package com.stealthpipe.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.stealthpipe.ModState;
import com.stealthpipe.StealthPipe;
//? if <26.1
//import net.minecraft.client.gui.GuiGraphics;
//? if >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.time.Instant;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenMixin {



    @Unique
    private String formatBytes(int bytes) {
        if (bytes < 1024) return bytes + " B";

        // Calculate the index in the unit array using log base 1024
        int exp = (int) (Math.log(bytes) / Math.log(1024));

        // Define units (SI/Binary standard: KiB, MiB or KB, MB)
        String[] units = {"KB", "MB", "GB", "TB", "PB", "EB"};
        String unit = units[exp - 1];

        // Calculate the value and format to 2 decimal places
        double value = bytes / Math.pow(1024, exp);

        return String.format("%.2f %s", value, unit);
    }

    @Unique
    private String getColorFromPing(int ping) {
        if (ping <= 100) {
            return "§a";
        } else if (ping <= 150) {
            return "§e";
        } else {
            return "§c";
        }
    }

    @Unique
    private void updateDebugCounters() {

        ModState.inboundBandwidth.update();
        ModState.outboundBandwidth.update();
        ModState.inboundPPS.update();
        ModState.outboundPPS.update();

    }

    @Unique
    private void addToDebugList(List<String> list2) {
        list2.add("§aStealthPipe " + StealthPipe.REAL_MOD_VERSION);
        list2.add("Usage in out: " + formatBytes(ModState.inboundData.get()) + " " + formatBytes(ModState.outboundData.get()));
        list2.add("Data in out: " + formatBytes(ModState.inboundBandwidth.get()) + "/s " + formatBytes(ModState.outboundBandwidth.get()) + "/s");
        list2.add(
                String.format("Ping/RTT: %s%s", this.getColorFromPing(ModState.ping.get()), ModState.ping.get() + "ms")
        );

        list2.add("PPS in out: " + ModState.inboundPPS.get() + "/s " + ModState.outboundPPS.get() + "/s");
        list2.add("Is Client: " + ModState.isClientConnectingToStealthServer.get());
        list2.add("WS Open: " + ModState.webSocketOpen.get());
        list2.add("Game open to LAN: " + ModState.gameOpenToLan.get());
        list2.add("Using WebRTC: " + ModState.usingWebRTC.get());
    }

    /*? if >=26.1 {*/
    @ModifyArgs(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V",
                    ordinal = 1
            )
    )
    public void extractRenderStateInject(Args args) {
        List<String> rightLines = args.get(1);
        this.updateDebugCounters();
        this.addToDebugList(rightLines);
    }

    /*?} else if >=1.21.9 && < 26.1 {*/
    /*@Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V",
                    ordinal = 1 // ordinal 0 is 'list', ordinal 1 is 'list2'
            )
    )
    private void render(GuiGraphics guiGraphics, CallbackInfo ci, @Local(ordinal = 1) List<String> list2) {
        this.updateDebugCounters();
        this.addToDebugList(list2);
    }

    *//*? } else { */
    /*@Inject(
            method = "drawGameInformation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V",
                    ordinal = 0 // ordinal 0 is 'list', ordinal 1 is 'list2'
            )
    )
    private void render2(GuiGraphics guiGraphics, CallbackInfo ci, @Local(ordinal = 0) List<String> list2) {

        this.updateDebugCounters();
        this.addToDebugList(list2);
    }
    *//*? } */
}
