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
package org.drasyl.cli.tun.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.JavaDrasylServerChannel;
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.cli.handler.PrintAndCloseOnExceptionHandler;
import org.drasyl.cli.tun.handler.DrasylToTunHandler;
import org.drasyl.cli.tun.handler.TunPacketCodec;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.handler.crypto.ArmHeaderCodec;
import org.drasyl.node.handler.crypto.LongTimeArmHandler;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class TunChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private static final Logger LOG = LoggerFactory.getLogger(TunChildChannelInitializer.class);
    private static final Duration ARM_SESSION_TIME = Duration.ofMinutes(5);
    private final PrintStream err;
    private final Channel tun;
    private final Map<InetAddress, DrasylAddress> routes;
    private final boolean applicationArmEnabled;

    public TunChildChannelInitializer(final PrintStream err,
                                      final Channel tun,
                                      final Map<InetAddress, DrasylAddress> routes,
                                      final boolean applicationArmEnabled) {
        this.err = requireNonNull(err);
        this.tun = requireNonNull(tun);
        this.routes = requireNonNull(routes);
        this.applicationArmEnabled = applicationArmEnabled;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    protected void initChannel(final DrasylChannel ch) throws CryptoException {
        if (!routes.containsValue(ch.remoteAddress())) {
            LOG.debug("Close channel for `{}` that is not in my peers list.", ch.remoteAddress());
            ch.close();
            return;
        }

        final ChannelPipeline p = ch.pipeline();

        if (applicationArmEnabled) {
            p.addLast(new ArmHeaderCodec());
            final int maxPeers;
            if (ch.parent() instanceof JavaDrasylServerChannel) {
                maxPeers = ((JavaDrasylServerChannel) ch.parent()).config().getMaxPeers();
            }
            else {
                maxPeers = Math.toIntExact(((RustDrasylServerChannel) ch.parent()).config().getMaxPeers());
            }
            p.addLast(new LongTimeArmHandler(ARM_SESSION_TIME, maxPeers, ch.identity(), (IdentityPublicKey) ch.remoteAddress()));
        }

        p.addLast(new TunPacketCodec());
        p.addLast(new DrasylToTunHandler(tun));

        p.addLast(new PrintAndCloseOnExceptionHandler(err));
    }
}
