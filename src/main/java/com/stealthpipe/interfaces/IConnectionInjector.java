package com.stealthpipe.interfaces;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

public interface IConnectionInjector {
    void injectVirtualConnection(EmbeddedChannel virtualChannel);
}
