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
package org.drasyl.cli.wormhole.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.channel.ConnectionHandshakeChannelInitializer;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.wormhole.WormholeSendCommand.Payload;
import org.drasyl.cli.wormhole.handler.WormholeFileSender;
import org.drasyl.cli.wormhole.handler.WormholeTextSender;
import org.drasyl.cli.wormhole.message.WormholeMessage;
import org.drasyl.handler.arq.gobackn.ByteToGoBackNArqDataCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqCodec;
import org.drasyl.handler.arq.gobackn.GoBackNArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.handler.crypto.ArmHeaderCodec;
import org.drasyl.node.handler.crypto.LongTimeArmHandler;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormhole.channel.WormholeSendChannelInitializer.MAX_PEERS;
import static org.drasyl.util.Preconditions.requirePositive;

public class WormholeSendChildChannelInitializer extends ConnectionHandshakeChannelInitializer {
    public static final int ARQ_RETRY_TIMEOUT = 150;
    public static final int ARQ_WINDOW_SIZE = 50;
    public static final Duration ARM_SESSION_TIME = Duration.ofMinutes(5);
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Identity identity;
    private final String password;
    private final Payload payload;
    private final int windowSize;
    private final Duration windowTimeout;

    @SuppressWarnings("java:S107")
    public WormholeSendChildChannelInitializer(final PrintStream out,
                                               final PrintStream err,
                                               final Worm<Integer> exitCode,
                                               final Identity identity,
                                               final String password,
                                               final Payload payload,
                                               final int windowSize,
                                               final long windowTimeout) {
        super(false);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.identity = requireNonNull(identity);
        this.password = requireNonNull(password);
        this.payload = requireNonNull(payload);
        this.windowSize = requirePositive(windowSize);
        this.windowTimeout = Duration.ofMillis(requirePositive(windowTimeout));
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        // close parent as well
        ch.closeFuture().addListener(f -> ch.parent().close());

        final ChannelPipeline p = ch.pipeline();
        p.addLast(new ArmHeaderCodec());
        p.addLast(new LongTimeArmHandler(ARM_SESSION_TIME, MAX_PEERS, identity, (IdentityPublicKey) ch.remoteAddress()));

        super.initChannel(ch);
    }

    @Override
    protected void handshakeCompleted(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        // add ARQ to make sure messages arrive
        ch.pipeline().addLast(new GoBackNArqCodec());
        ch.pipeline().addLast(new GoBackNArqHandler(windowSize, windowTimeout, windowTimeout.dividedBy(5)));
        ch.pipeline().addLast(new ByteToGoBackNArqDataCodec());

        // (de)serializer for WormholeMessages
        ch.pipeline().addLast(new JacksonCodec<>(WormholeMessage.class));

        if (payload.getText() != null) {
            p.addLast(new WormholeTextSender(out, password, payload.getText()));
        }
        else {
            p.addLast(new WormholeFileSender(out, password, payload.getFile()));
        }

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        cause.printStackTrace(err);
        ctx.channel().close();
        exitCode.trySet(1);
    }
}
