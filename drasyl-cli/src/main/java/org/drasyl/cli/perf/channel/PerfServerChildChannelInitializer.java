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
import org.drasyl.channel.ConnectionHandshakeChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.perf.handler.PerfSessionAcceptorHandler;
import org.drasyl.cli.perf.handler.ProbeCodec;
import org.drasyl.cli.perf.message.PerfMessage;
import org.drasyl.handler.arq.gobackn.ByteToGoBackNArqDataCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqReceiverHandler;
import org.drasyl.handler.arq.gobackn.GoBackNArqSenderHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

public class PerfServerChildChannelInitializer extends ConnectionHandshakeChannelInitializer {
    public static final int ARQ_RETRY_TIMEOUT = 250;
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;

    public PerfServerChildChannelInitializer(final PrintStream out,
                                             final PrintStream err,
                                             final Worm<Integer> exitCode) {
        super(false);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
    }

    @Override
    protected void handshakeCompleted(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        // fast (de)serializer for Probe messages
        p.addLast(new ProbeCodec());

        // add ARQ to make sure messages arrive
        p.addLast(new GoBackNArqCodec());
        p.addLast(new GoBackNArqSenderHandler(150, Duration.ofMillis(ARQ_RETRY_TIMEOUT)));
        p.addLast(new GoBackNArqReceiverHandler(Duration.ofMillis(ARQ_RETRY_TIMEOUT).dividedBy(5)));
        p.addLast(new ByteToGoBackNArqDataCodec());

        // (de)serializer for PerfMessages
        p.addLast(new JacksonCodec<>(PerfMessage.class));

        // perf
        p.addLast(new PerfSessionAcceptorHandler(out));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        out.println("Close connection to " + ctx.channel().remoteAddress() + " as handshake was not fulfilled within " + handshakeTimeout.toMillis() + "ms.");
        ctx.close();
    }
}
