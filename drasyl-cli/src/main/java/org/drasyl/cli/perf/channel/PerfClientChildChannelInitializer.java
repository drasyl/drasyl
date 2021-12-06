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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndCloseOnExceptionHandler;
import org.drasyl.cli.perf.handler.PerfSessionRequestorHandler;
import org.drasyl.cli.perf.handler.ProbeCodec;
import org.drasyl.cli.perf.message.PerfMessage;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.arq.gobackn.ByteToGoBackNArqDataCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.perf.channel.PerfServerChildChannelInitializer.ARQ_RETRY_TIMEOUT;

public class PerfClientChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private static final Logger LOG = LoggerFactory.getLogger(PerfClientChildChannelInitializer.class);
    public static final int REQUEST_TIMEOUT_MILLIS = 10_000;
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
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.server = requireNonNull(server);
        this.waitForDirectConnection = waitForDirectConnection;
        this.sessionRequest = requireNonNull(sessionRequest);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws CryptoException {
        if (!server.equals(ch.remoteAddress())) {
            LOG.debug("Close channel for peer `{}` that is not my server.", ch.remoteAddress());
            ch.close();
            return;
        }

        final ChannelPipeline p = ch.pipeline();

        // fast (de)serializer for Probe messages
        p.addLast(new ProbeCodec());

        // add ARQ to make sure messages arrive
        ch.pipeline().addLast(new GoBackNArqCodec());
        ch.pipeline().addLast(new GoBackNArqHandler(150, Duration.ofMillis(ARQ_RETRY_TIMEOUT), Duration.ofMillis(ARQ_RETRY_TIMEOUT).dividedBy(5)));
        ch.pipeline().addLast(new ByteToGoBackNArqDataCodec());

        // (de)serializer for PerfMessages
        p.addLast(new JacksonCodec<>(PerfMessage.class));

        p.addLast(new PerfSessionRequestorHandler(out, sessionRequest, REQUEST_TIMEOUT_MILLIS, waitForDirectConnection));
        p.addLast(new PrintAndCloseOnExceptionHandler(err, exitCode));

        // close parent as well
        ch.closeFuture().addListener(f -> ch.parent().close());
    }
}
