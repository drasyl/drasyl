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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.cli.node.handler.JsonRpc2DrasylNodeHandler;
import org.drasyl.cli.node.handler.JsonRpc2ExceptionHandler;
import org.drasyl.cli.node.handler.JsonRpc2RequestDecoder;
import org.drasyl.cli.node.handler.JsonRpc2ResponeEncoder;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;

import java.util.Queue;

import static java.util.Objects.requireNonNull;

/**
 * Creates a JSON-RPC 2.0 over TCP server channel.
 */
public class NodeRcJsonRpc2OverTcpServerInitializer extends ChannelInitializer<Channel> {
    private final DrasylNode node;
    private final Queue<Event> events;

    public NodeRcJsonRpc2OverTcpServerInitializer(final DrasylNode node,
                                                  final Queue<Event> events) {
        this.node = requireNonNull(node);
        this.events = requireNonNull(events);
    }

    @Override
    protected void initChannel(final Channel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JsonRpc2ResponeEncoder());
        p.addLast(new JsonRpc2RequestDecoder());
        p.addLast(new JsonRpc2DrasylNodeHandler(node, events));
        p.addLast(new JsonRpc2ExceptionHandler());
    }
}
