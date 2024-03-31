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
package org.drasyl.cli.perf.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.drasyl.channel.ConnectionChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.perf.handler.PerfSessionReceiverHandler;
import org.drasyl.cli.perf.handler.PerfSessionRequestorHandler;
import org.drasyl.cli.perf.handler.ProbeCodec;
import org.drasyl.cli.perf.message.PerfMessage;
import org.drasyl.cli.perf.message.Probe;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.SegmentCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.perf.PerfCommand.CONNECTION_CONFIG;

public class PerfClientChildChannelInitializer extends ConnectionChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(PerfClientChildChannelInitializer.class);
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey server;
    private final boolean waitForDirectConnection;
    private final SessionRequest sessionRequest;

    public PerfClientChildChannelInitializer(final PrintStream out,
                                             final PrintStream err,
                                             final Worm<Integer> exitCode,
                                             final IdentityPublicKey server,
                                             final boolean waitForDirectConnection,
                                             final SessionRequest sessionRequest) {
        super(false, DEFAULT_SERVER_PORT, CONNECTION_CONFIG);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.server = requireNonNull(server);
        this.waitForDirectConnection = waitForDirectConnection;
        this.sessionRequest = requireNonNull(sessionRequest);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        if (!server.equals(ch.remoteAddress())) {
            LOG.debug("Close channel for peer `{}` that is not my server.", ch.remoteAddress());
            ch.close();
            return;
        }

        // close parent as well
        ch.closeFuture().addListener(f -> ch.parent().close());

        super.initChannel(ch);
    }

    @Override
    protected void handshakeCompleted(final ChannelHandlerContext ctx) {
        final ChannelPipeline p = ctx.pipeline();

        p.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        p.addLast(new LengthFieldPrepender(4));

        // fast (de)serializer for Probe messages
        p.addBefore(p.context(SegmentCodec.class).name(), null, new ProbeCodec()); // bypass reliability layer
        p.addBefore(p.context(SegmentCodec.class).name(), null, new SimpleChannelInboundHandler<Probe>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final Probe msg) {
                ctx.pipeline().context(PerfSessionReceiverHandler.class).fireChannelRead(msg.retain()); // bypass reliability layer
            }
        });

        // (de)serializer for PerfMessages
        p.addLast(new JacksonCodec<>(PerfMessage.class));

        // FIXME: lets Probe message skip ConnectionHandler

        // perf
        p.addLast(new PerfSessionRequestorHandler(out, sessionRequest, waitForDirectConnection));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        new Exception("Unable to connect to server.", cause).printStackTrace(err);
        ctx.channel().close();
        exitCode.trySet(1);
    }
}
