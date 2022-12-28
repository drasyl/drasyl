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
package org.drasyl.cli.wormholearq.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.cli.wormholearq.message.TextMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

public class WormholeTextSender extends AbstractWormholeSender {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeTextSender.class);
    private final String text;

    public WormholeTextSender(final PrintStream out,
                              final String password,
                              final String text) {
        super(out, password);
        this.text = requireNonNull(text);
    }

    @SuppressWarnings("java:S1905")
    @Override
    protected void transferPayload(final ChannelHandlerContext ctx) {
        ctx.pipeline().remove(ctx.name());
        ctx.writeAndFlush(new TextMessage(text)).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                out.println("text message sent");
                ctx.close();
            }
            else {
                f.channel().pipeline().fireExceptionCaught(f.cause());
            }
        });
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
