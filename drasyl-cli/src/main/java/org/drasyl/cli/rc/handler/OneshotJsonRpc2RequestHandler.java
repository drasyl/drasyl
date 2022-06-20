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
package org.drasyl.cli.rc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.function.Consumer;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

public class OneshotJsonRpc2RequestHandler extends SimpleChannelInboundHandler<JsonRpc2Response> {
    private static final Logger LOG = LoggerFactory.getLogger(OneshotJsonRpc2RequestHandler.class);
    private final JsonRpc2Request request;
    private final Consumer<JsonRpc2Response> responseConsumer;

    public OneshotJsonRpc2RequestHandler(final JsonRpc2Request request,
                                         final Consumer<JsonRpc2Response> responseConsumer) {
        this.request = requireNonNull(request);
        this.responseConsumer = responseConsumer;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        LOG.trace("Send request `{}`.", request);
        ctx.writeAndFlush(request).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final JsonRpc2Response response) throws Exception {
        LOG.trace("Got response `{}`.", response);
        if (request.getId().equals(response.getId())) {
            responseConsumer.accept(response);
            ctx.channel().close();
        }
    }
}
