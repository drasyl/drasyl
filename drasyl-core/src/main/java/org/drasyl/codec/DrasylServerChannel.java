/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.codec;

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;

import java.net.SocketAddress;

public class DrasylServerChannel extends AbstractServerChannel {
    private volatile int state; // 0 - open (node created), 1 - active (node started), 2 - closed (node shut down)
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private volatile DrasylAddress localAddress;

    public DrasylServerChannel() {
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) {
        System.out.println("MyServerChannel.doBind");
        // node start shit
        this.localAddress = (DrasylAddress) localAddress;
        state = 1;
    }

    @Override
    protected void doClose() throws Exception {
        System.out.println("MyServerChannel.doClose");
        // node shutdown shit

        if (state <= 1) {
            // Update all internal state before the closeFuture is notified.
            if (localAddress != null) {
                localAddress = null;
            }
            state = 2;
        }
    }

    @Override
    protected void doBeginRead() throws Exception {
        System.out.println("MyServerChannel.doBeginRead");

        // do nothing.
        // UdpServer, UdpMulticastServer, TcpServer will push their readings to us
        // TODO: let us pull readings from them
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state < 2;
    }

    @Override
    public boolean isActive() {
        return state == 1;
    }
}
