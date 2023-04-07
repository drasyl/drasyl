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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.cli.wormhole.message.PasswordMessage;
import org.drasyl.cli.wormhole.message.WrongPasswordMessage;
import org.drasyl.util.logging.Logger;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.ABORTED;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.COMPLETED;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.ERRORED;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.INITIALIZED;
import static org.drasyl.cli.wormhole.handler.AbstractWormholeSender.State.TRANSFERRING;
import static org.drasyl.util.SecretUtil.maskSecret;

abstract class AbstractWormholeSender extends ChannelDuplexHandler {
    protected final PrintStream out;
    protected final String password;
    protected State state = INITIALIZED;

    protected AbstractWormholeSender(final PrintStream out, final String password) {
        this.out = requireNonNull(out);
        this.password = requireNonNull(password);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (state == INITIALIZED && msg instanceof PasswordMessage) {
            handlePasswordMessage(ctx, ((PasswordMessage) msg).getPassword());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (state != COMPLETED) {
            state = ABORTED;
            abortTransfer(ctx);
        }
        ctx.close(promise);
    }

    @SuppressWarnings("java:S1905")
    private void handlePasswordMessage(final ChannelHandlerContext ctx, final String password) {
        if (this.password.equals(password)) {
            // correct password -> send payload
            state = TRANSFERRING;
            log().debug("Got request from `{}` with correct password `{}`. Reply with payload.", () -> ctx.channel().remoteAddress(), () -> maskSecret(password));
            transferPayload(ctx);
        }
        else {
            // wrong password -> send rejection
            state = ERRORED;
            log().debug("Got request from `{}` with wrong password `{}`. Reply with decline and close connection.", () -> ctx.channel().remoteAddress(), () -> maskSecret(password));

            ctx.writeAndFlush(new WrongPasswordMessage()).addListener((ChannelFutureListener) f -> f.channel().pipeline().fireExceptionCaught(new Exception(
                    "Code confirmation failed. Either you or your correspondent\n" +
                            "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                            "incorrectly. You could try again, giving both your correspondent and\n" +
                            "the attacker another chance."
            )));
        }
    }

    protected abstract void transferPayload(ChannelHandlerContext ctx);

    protected void abortTransfer(final ChannelHandlerContext ctx) {
        out.println("abort");
    }

    protected abstract Logger log();

    enum State {
        INITIALIZED,
        TRANSFERRING,
        ERRORED,
        COMPLETED,
        ABORTED
    }
}
