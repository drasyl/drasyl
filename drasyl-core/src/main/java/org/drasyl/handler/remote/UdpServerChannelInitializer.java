/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.JavaDrasylServerChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.crypto.UnarmedMessageDecoder;
import org.drasyl.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;

@UnstableApi
public class UdpServerChannelInitializer extends ChannelInitializer<DatagramChannel> {
    private final JavaDrasylServerChannel parent;

    public UdpServerChannelInitializer(final JavaDrasylServerChannel parent) {
        this.parent = requireNonNull(parent);
    }

    @Override
    protected void initChannel(final DatagramChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new DatagramCodec());
        p.addLast(new ByteToRemoteMessageCodec());
        p.addLast(new OtherNetworkFilter(parent.config().getNetworkId()));
        p.addLast(new InvalidProofOfWorkFilter());
        if (parent.config().isArmingEnabled()) {
            ch.pipeline().addLast(new ProtocolArmHandler(
                    parent.identity(),
                    Crypto.INSTANCE,
                    parent.config().getArmingSessionMaxCount(),
                    parent.config().getArmingSessionExpireAfter()
            ));
        }
        ch.pipeline().addLast(new UnarmedMessageDecoder());
        ch.pipeline().addLast(new UdpServerToDrasylHandler(parent));
    }
}
