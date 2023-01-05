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
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.drasyl.cli.node.handler.HttpToBytesCodec;
import org.drasyl.cli.rc.handler.JsonRpc2BadHttpRequestHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.util.network.Subnet;

import java.net.InetAddress;
import java.util.Map;

/**
 * Creates a JSON-RPC 2.0 over HTTP server channel.
 */
@SuppressWarnings("java:S110")
public class TunRcJsonRpc2OverHttpServerInitializer extends TunRcJsonRpc2OverTcpServerInitializer {
    public static final int HTTP_MAX_CONTENT_LENGTH = 1_024 * 1_024; // bytes
    public static final int HTTP_REQUEST_TIMEOUT = 60; // seconds

    public TunRcJsonRpc2OverHttpServerInitializer(final Map<InetAddress, DrasylAddress> routes,
                                                  final Identity identity,
                                                  final Subnet subnet,
                                                  final Channel channel,
                                                  final InetAddress address) {
        super(routes, identity, subnet, channel, address);
    }

    @Override
    protected void initChannel(final Channel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(HTTP_MAX_CONTENT_LENGTH));
        p.addLast(new ReadTimeoutHandler(HTTP_REQUEST_TIMEOUT));
        p.addLast(new JsonRpc2BadHttpRequestHandler());
        p.addLast(new HttpToBytesCodec());

        super.initChannel(ch);

        p.remove(DelimiterBasedFrameDecoder.class);
    }
}
