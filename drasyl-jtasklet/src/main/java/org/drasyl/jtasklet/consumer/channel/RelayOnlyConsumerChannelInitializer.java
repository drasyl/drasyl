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
package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.RelayOnlyDrasylServerChannelInitializer;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.handler.ConsumerHandler;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

public class RelayOnlyConsumerChannelInitializer extends RelayOnlyDrasylServerChannelInitializer {
    private final PrintStream out;
    private final IdentityPublicKey broker;
    private final String source;
    private final Object[] input;
    private final int cycles;
    private final List<String> tags;
    private final int priority;
    private final long onlineTimeoutMillis;

    @SuppressWarnings("java:S107")
    public RelayOnlyConsumerChannelInitializer(final Identity identity,
                                      final EventLoopGroup udpServerGroup,
                                      final InetSocketAddress bindAddress,
                                      final int networkId,
                                      final long onlineTimeoutMillis,
                                      final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                      final PrintStream out,
                                      final boolean protocolArmEnabled,
                                      final IdentityPublicKey broker,
                                      final String source,
                                      final Object[] input,
                                      final int cycles,
                                      final List<String> tags,
                                      final int priority) {
        super(identity, udpServerGroup, bindAddress, networkId, superPeers, protocolArmEnabled, PING_INTERVAL_MILLIS, PING_TIMEOUT_MILLIS, MAX_TIME_OFFSET_MILLIS, MAX_PEERS);
        this.out = requireNonNull(out);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.cycles = requirePositive(cycles);
        this.tags = requireNonNull(tags);
        this.priority = requireNonNegative(priority);
        this.onlineTimeoutMillis = onlineTimeoutMillis;
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().addLast(new SuperPeerTimeoutHandler(onlineTimeoutMillis));
        ch.pipeline().addLast(new PeersRttHandler(2_500L));
        ch.pipeline().addLast(new ConsumerHandler(out, identity.getAddress(), broker, source, input, cycles, tags, priority));
    }
}
