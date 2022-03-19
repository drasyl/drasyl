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
package org.drasyl.cli.noderc.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.cli.noderc.handler.JsonRpc2RequestEncoder;
import org.drasyl.cli.noderc.handler.JsonRpc2ResponseDecoder;
import org.drasyl.cli.noderc.handler.OneshotJsonRpc2RequestHandler;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class NodeRcJsonRpc2OverTcpClientInitializer extends ChannelInitializer<Channel> {
    private final JsonRpc2Request request;
    private final Consumer<JsonRpc2Response> responseConsumer;

    public NodeRcJsonRpc2OverTcpClientInitializer(final JsonRpc2Request request,
                                                  final Consumer<JsonRpc2Response> responseConsumer) {
        this.request = requireNonNull(request);
        this.responseConsumer = requireNonNull(responseConsumer);
    }

    @Override
    public void initChannel(final Channel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JsonRpc2RequestEncoder());
        p.addLast(new JsonRpc2ResponseDecoder());
        p.addLast(new OneshotJsonRpc2RequestHandler(request, responseConsumer));
    }
}
