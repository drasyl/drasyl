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
package org.drasyl.cli.tunnel.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.ConnectionHandshakeChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.tunnel.TunnelExposeCommand.Service;
import org.drasyl.cli.tunnel.handler.ExposeDrasylHandler;
import org.drasyl.cli.tunnel.handler.TunnelWriteCodec;
import org.drasyl.cli.tunnel.message.JacksonCodecTunnelMessage;
import org.drasyl.handler.arq.gobackn.ByteToGoBackNArqDataCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqReceiverHandler;
import org.drasyl.handler.arq.gobackn.GoBackNArqSenderHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.handler.crypto.ArmHeaderCodec;
import org.drasyl.node.handler.crypto.LongTimeArmHandler;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.tunnel.TunnelExposeCommand.WRITE_TIMEOUT_SECONDS;
import static org.drasyl.cli.tunnel.channel.TunnelExposeChannelInitializer.MAX_PEERS;
import static org.drasyl.cli.wormhole.channel.WormholeSendChildChannelInitializer.ARQ_WINDOW_SIZE;

public class TunnelExposeChildChannelInitializer extends ConnectionHandshakeChannelInitializer {
    public static final int ARQ_RETRY_TIMEOUT = 250;
    public static final Duration ARM_SESSION_TIME = Duration.ofMinutes(5);
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Identity identity;
    private final String password;
    private final Service service;

    public TunnelExposeChildChannelInitializer(final PrintStream err,
                                               final Worm<Integer> exitCode,
                                               final Identity identity,
                                               final String password,
                                               final Service service) {
        super(false);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.identity = requireNonNull(identity);
        this.password = requireNonNull(password);
        this.service = requireNonNull(service);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();
        p.addLast(new ArmHeaderCodec());
        p.addLast(new LongTimeArmHandler(ARM_SESSION_TIME, MAX_PEERS, identity, (IdentityPublicKey) ch.remoteAddress()));

        super.initChannel(ch);
    }

    @Override
    protected void handshakeCompleted(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        // add ARQ to make sure messages arrive
        p.addLast(new GoBackNArqCodec());
        p.addLast(new GoBackNArqSenderHandler(ARQ_WINDOW_SIZE, Duration.ofMillis(ARQ_RETRY_TIMEOUT)));
        p.addLast(new GoBackNArqReceiverHandler(Duration.ofMillis(50)));
        p.addLast(new ByteToGoBackNArqDataCodec());
        p.addLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS));

        // (de)serializers for TunnelMessages
        p.addLast(new TunnelWriteCodec());
        p.addLast(new JacksonCodec<>(JacksonCodecTunnelMessage.class));

        p.addLast(new ExposeDrasylHandler(password, service.getTcp()));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        new Exception("The exposing node did not respond within " + handshakeTimeout.toMillis() + "ms. Try again later.", cause).printStackTrace(err);
        ctx.channel().close();
        exitCode.trySet(1);
    }
}
