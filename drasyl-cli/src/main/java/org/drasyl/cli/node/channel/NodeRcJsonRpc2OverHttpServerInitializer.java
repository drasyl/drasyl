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
package org.drasyl.cli.node.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.drasyl.cli.node.handler.HttpToBytesCodec;
import org.drasyl.cli.rc.handler.JsonRpc2BadHttpRequestHandler;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;

import java.util.Queue;

/**
 * Creates a JSON-RPC 2.0 over HTTP server channel.
 */
@SuppressWarnings("java:S110")
public class NodeRcJsonRpc2OverHttpServerInitializer extends NodeRcJsonRpc2OverTcpServerInitializer {
    public static final int HTTP_MAX_CONTENT_LENGTH = 1_048_576; // bytes
    public static final int HTTP_REQUEST_TIMEOUT = 60; // seconds

    public NodeRcJsonRpc2OverHttpServerInitializer(final DrasylNode node,
                                                   final Queue<Event> events) {
        super(node, events);
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
