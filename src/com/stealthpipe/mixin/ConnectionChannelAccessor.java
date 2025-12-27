package com.stealthpipe.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientConnection.class)
public interface ConnectionChannelAccessor {

    @Accessor("channel")
    Channel getChannel();

    @Accessor("channel")
    void setChannel(Channel virtualChannel);

    @Accessor("address")
    void setAddress(java.net.SocketAddress address);
}
