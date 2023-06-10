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
package org.drasyl.cli.sdo.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.sdo.handler.SdoNodeHandler;
import org.drasyl.handler.noop.NoopDiscardHandler;
import org.drasyl.handler.peers.PeersHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@SuppressWarnings("java:S110")
public class SdoNodeChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey controller;

    @SuppressWarnings("java:S107")
    public SdoNodeChannelInitializer(final Identity identity,
                                     final EventLoopGroup udpServerGroup,
                                     final InetSocketAddress bindAddress,
                                     final int networkId,
                                     final long onlineTimeoutMillis,
                                     final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                     final PrintStream out,
                                     final PrintStream err,
                                     final Worm<Integer> exitCode,
                                     final boolean protocolArmEnabled,
                                     final IdentityPublicKey controller) {
        super(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.controller = requireNonNull(controller);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        final ChannelPipeline p = ch.pipeline();

        final PeersHandler peersHandler = new PeersHandler();
        ch.pipeline().addLast(peersHandler);
        ch.eventLoop().scheduleAtFixedRate(() -> out.println(peersHandler.getPeers()), 5000, 5000, MILLISECONDS);

        p.addLast(new SdoNodeHandler(controller));
        p.addLast(new NoopDiscardHandler());

        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(final ChannelHandlerContext ctx) {
                out.println("----------------------------------------------------------------------------------------------");
                out.println("Node listening on address " + ch.localAddress());
                out.println("----------------------------------------------------------------------------------------------");

                ctx.fireChannelActive();
                ctx.pipeline().remove(this);
            }
        });
        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }
}
