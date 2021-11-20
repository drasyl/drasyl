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
package org.drasyl.cli.wormhole.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.drasyl.cli.wormhole.message.FileMessage;
import org.drasyl.cli.wormhole.message.PasswordMessage;
import org.drasyl.cli.wormhole.message.TextMessage;
import org.drasyl.cli.wormhole.message.WormholeMessage;
import org.drasyl.cli.wormhole.message.WrongPasswordMessage;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.SecretUtil.maskSecret;

public class WormholeReceiver extends SimpleChannelInboundHandler<WormholeMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeReceiver.class);
    private final PrintStream out;
    private final String password;
    private final long requestTimeoutMillis;
    private Future<?> timeoutTask;

    public WormholeReceiver(final PrintStream out,
                            final String password,
                            final long requestTimeoutMillis) {
        this.out = requireNonNull(out);
        this.password = requireNonNull(password);
        this.requestTimeoutMillis = requirePositive(requestTimeoutMillis);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // request text
        LOG.debug("Requesting text from `{}` with password `{}`", () -> ctx.channel().remoteAddress(), () -> maskSecret(password));
        ctx.writeAndFlush(new PasswordMessage(password)).addListener(FIRE_EXCEPTION_ON_FAILURE);

        // create timeout task
        timeoutTask = ctx.executor().schedule(() -> {
            if (ctx.channel().isOpen()) {
                ctx.fireExceptionCaught(new WormholeRequestTimeoutException(requestTimeoutMillis));
            }
        }, requestTimeoutMillis, MILLISECONDS);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final WormholeMessage msg) {
        if (msg instanceof WrongPasswordMessage) {
            timeoutTask.cancel(false);
            ctx.fireExceptionCaught(new Exception("Code confirmation failed. Either you or your correspondent\n" +
                    "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                    "incorrectly. You could try again, giving both your correspondent and\n" +
                    "the attacker another chance."));
        }
        else if (msg instanceof TextMessage) {
            timeoutTask.cancel(false);
            final TextMessage textMsg = (TextMessage) msg;
            LOG.debug("Got text from `{}`: {}", () -> ctx.channel().remoteAddress(), textMsg::getText);
            out.println(textMsg.getText());
            ctx.close();
        }
        else if (msg instanceof FileMessage) {
            timeoutTask.cancel(false);
            ctx.channel().pipeline().addBefore(ctx.channel().pipeline().context(JacksonCodec.class).name(), null, new WormholeFileReceiver(out, (FileMessage) msg));
            ctx.channel().pipeline().remove(ctx.name());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    public static class WormholeRequestTimeoutException extends Exception {
        public WormholeRequestTimeoutException(final long timeoutMillis) {
            super("The sender did not respond within " + timeoutMillis + "ms. Try again later.");
        }
    }
}
