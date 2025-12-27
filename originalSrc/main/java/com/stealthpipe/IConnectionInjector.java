package com.stealthpipe;

import io.netty.channel.Channel;

public interface IConnectionInjector {
    void injectVirtualConnection(Channel virtualChannel);
}
