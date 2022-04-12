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
package org.drasyl.jtasklet.consumer.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.message.ResourceRequest;
import org.drasyl.jtasklet.message.ResourceResponse;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

public class ResourceRequestHandler extends SimpleChannelInboundHandler<ResourceResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceRequestHandler.class);
    private final PrintStream out;
    private final AtomicReference<IdentityPublicKey> provider;

    public ResourceRequestHandler(final PrintStream out,
                                  final AtomicReference<IdentityPublicKey> provider) {
        this.out = requireNonNull(out);
        this.provider = requireNonNull(provider);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final ResourceRequest msg = new ResourceRequest();
        LOG.info("Send resource request `{}` to `{}`", msg, ctx.channel().remoteAddress());
        out.print("Request resource from broker " + ctx.channel().remoteAddress() + "...");
        ctx.writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE).addListener(f -> {
            if (f.isSuccess()) {
                out.println("done!");
            }
        });

        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ResourceResponse msg) {
        LOG.info("Got resource response `{}` from `{}`", msg, ctx.channel().remoteAddress());
        final IdentityPublicKey publicKey = msg.getPublicKey();
        if (publicKey == null) {
            out.println("No resources available. Please try again later.");
            ctx.close();
            return;
        }

        out.println("Broker provides us resources at VM " + publicKey);
        provider.set(publicKey);
        final DrasylChannel childChannel = new DrasylChannel((DrasylServerChannel) ctx.channel().parent(), publicKey);
        ctx.channel().parent().pipeline().fireChannelRead(childChannel);
    }
}
