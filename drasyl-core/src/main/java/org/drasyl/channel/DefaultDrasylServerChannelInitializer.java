/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.handler.LoopbackHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.crypto.UnarmedMessageDecoder;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.internet.UnconfirmedAddressResolveHandler;
import org.drasyl.util.internal.UnstableApi;

/**
 * The default {@link ChannelInitializer} for {@link DrasylServerChannel}s.
 */
@UnstableApi
public class DefaultDrasylServerChannelInitializer extends ChannelInitializer<DrasylServerChannel> {
    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new UdpServer(ctx -> new UdpServerChannelInitializer(ch) {
            @Override
            protected void lastStage(final DatagramChannel datagramCh) {
                datagramCh.pipeline().addLast(new OtherNetworkFilter(ch.config().getNetworkId()));
                if (ch.config().isArmingEnabled()) {
                    datagramCh.pipeline().addLast(new ProtocolArmHandler(
                            ch.identity(),
                            Crypto.INSTANCE,
                            ch.config().getArmingSessionMaxCount(),
                            ch.config().getArmingSessionExpireAfter()
                    ));
                }
                else {
                    datagramCh.pipeline().addLast(new UnarmedMessageDecoder());
                }

                super.lastStage(datagramCh);
            }
        }));
        p.addLast(new UnconfirmedAddressResolveHandler());
        if (ch.config().isHolePunchingEnabled()) {
            p.addLast(new TraversingInternetDiscoveryChildrenHandler(ch.config().getSuperPeers()));
        }
        else {
            p.addLast(new InternetDiscoveryChildrenHandler(ch.config().getSuperPeers()));
        }
        p.addLast(new ApplicationMessageToPayloadCodec());
        p.addLast(new LoopbackHandler());
    }
}
