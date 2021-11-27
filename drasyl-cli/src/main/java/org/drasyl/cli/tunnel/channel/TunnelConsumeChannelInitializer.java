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
package org.drasyl.cli.tunnel.channel;

import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.cli.handler.PrintAndCloseOnExceptionHandler;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class TunnelConsumeChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey exposer;

    @SuppressWarnings("java:S107")
    public TunnelConsumeChannelInitializer(final Identity identity,
                                           final InetSocketAddress bindAddress,
                                           final int networkId,
                                           final int onlineTimeoutMillis,
                                           final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                           final PrintStream err,
                                           final Worm<Integer> exitCode,
                                           final IdentityPublicKey exposer) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.exposer = requireNonNull(exposer);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        final ChannelPipeline p = ch.pipeline();
        p.addLast(new SpawnChildChannelToPeer(ch, exposer));
        p.addLast(new PrintAndCloseOnExceptionHandler(err, exitCode));
    }
}
