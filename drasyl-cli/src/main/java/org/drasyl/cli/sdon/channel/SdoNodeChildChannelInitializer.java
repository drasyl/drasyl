/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.ConnectionChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.sdon.event.ControllerHandshakeFailed;
import org.drasyl.cli.sdon.handler.SdonMessageChildHandler;
import org.drasyl.cli.sdon.handler.policy.IpPolicyHandler.DrasylToTunHandler;
import org.drasyl.cli.sdon.message.SdonMessage;
import org.drasyl.cli.tun.handler.TunPacketCodec;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.SegmentCodec;
import org.drasyl.handler.noop.NoopDiscardHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

public class SdoNodeChildChannelInitializer extends ConnectionChannelInitializer {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey controller;

    public SdoNodeChildChannelInitializer(final PrintStream out,
                                          final PrintStream err,
                                          final Worm<Integer> exitCode,
                                          final IdentityPublicKey controller) {
        super(false, DEFAULT_SERVER_PORT);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.controller = requireNonNull(controller);
    }

    @Override
    protected void handshakeCompleted(final ChannelHandlerContext ctx) {
        final ChannelPipeline p = ctx.pipeline();

        p.addLast(new JacksonCodec<>(SdonMessage.class));
        p.addBefore(p.context(SegmentCodec.class).name(), null, new TunPacketCodec());
        p.addLast(new DrasylToTunHandler());
        p.addLast(new SdonMessageChildHandler());
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        if (controller.equals(ctx.channel().remoteAddress())) {
            // handshake with controller failed
            ctx.channel().parent().pipeline().fireUserEventTriggered(new ControllerHandshakeFailed(cause));
        }
        else {
            // handshake with other node failed
            out.println("Close connection to " + ctx.channel().remoteAddress() + " as handshake was not fulfilled within " + config.userTimeout().toMillis() + "ms.");
            ctx.close();
        }
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();
        p.addLast(new NoopDiscardHandler());

        super.initChannel(ch);

        p.addLast(new JacksonCodec<>(SdonMessage.class));
        p.addBefore(p.context(SegmentCodec.class).name(), null, new TunPacketCodec());
        p.addLast(new DrasylToTunHandler());
        p.addLast(new SdonMessageChildHandler());
    }
}
