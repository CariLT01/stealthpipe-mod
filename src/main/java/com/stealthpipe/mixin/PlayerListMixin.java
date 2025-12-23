package com.stealthpipe.mixin;

import com.stealthpipe.ModState;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method="placeNewPlayer", at=@At("TAIL"))
    private void placeNewPlayer(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {

        Channel channel = ((ConnectionChannelAccessor) connection).getChannel();

        System.out.printf("New channel of ID: %s%n", channel.id().asLongText());

        if (ModState.pendingChannelUuid.get().isEmpty()) {
            return;
        }

        String pendingUuid = ModState.pendingChannelUuid.get();
        String channelId = channel.id().asLongText();

        ModState.relayUuidToChannelMap.put(pendingUuid, channel);
        ModState.minecraftChannelUuidToRelayUuidMap.put(channel, pendingUuid);
        ModState.pendingChannelUuid.set("");



    }
}
