package com.stealthpipe.interfaces;

import io.netty.channel.Channel;

public interface IConnectionInjector {
    void injectVirtualConnection(Channel virtualChannel);
}
