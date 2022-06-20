/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
import io.netty.channel.ChannelPipeline;
import org.drasyl.cli.rc.channel.RcJsonRpc2OverTcpServerInitializer;
import org.drasyl.cli.tun.handler.JsonRpc2TunHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.util.network.Subnet;

import java.net.InetAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Creates a JSON-RPC 2.0 over TCP server channel.
 */
public class TunRcJsonRpc2OverTcpServerInitializer extends RcJsonRpc2OverTcpServerInitializer {
    private final Map<InetAddress, DrasylAddress> routes;
    private final Identity identity;
    private final Subnet subnet;
    private final Channel channel;
    private final InetAddress address;

    public TunRcJsonRpc2OverTcpServerInitializer(final Map<InetAddress, DrasylAddress> routes,
                                                 final Identity identity,
                                                 final Subnet subnet,
                                                 final Channel channel,
                                                 final InetAddress address) {
        this.routes = requireNonNull(routes);
        this.identity = requireNonNull(identity);
        this.subnet = requireNonNull(subnet);
        this.channel = requireNonNull(channel);
        this.address = requireNonNull(address);
    }

    @Override
    protected void jsonRpc2RequestStage(final ChannelPipeline p) {
        p.addLast(new JsonRpc2TunHandler(routes, identity, subnet, channel, address));
    }
}
