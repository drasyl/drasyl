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
package org.drasyl.cli.wormholearq.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.OldConnectionHandshakeChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.wormholearq.handler.WormholeReceiver;
import org.drasyl.cli.wormholearq.message.WormholeMessage;
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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormholearq.channel.WormholeSendChannelInitializer.MAX_PEERS;
import static org.drasyl.cli.wormholearq.channel.WormholeSendChildChannelInitializer.ARM_SESSION_TIME;
import static org.drasyl.cli.wormholearq.channel.WormholeSendChildChannelInitializer.ARQ_RETRY_TIMEOUT;
import static org.drasyl.cli.wormholearq.channel.WormholeSendChildChannelInitializer.ARQ_WINDOW_SIZE;
import static org.drasyl.util.Preconditions.requirePositive;

public class WormholeReceiveChildChannelInitializer extends OldConnectionHandshakeChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeReceiveChildChannelInitializer.class);
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Identity identity;
    private final IdentityPublicKey sender;
    private final String password;
    private final Duration ackInterval;

    public WormholeReceiveChildChannelInitializer(final PrintStream out,
                                                  final PrintStream err,
                                                  final Worm<Integer> exitCode,
                                                  final Identity identity,
                                                  final IdentityPublicKey sender,
                                                  final String password,
                                                  final long ackInterval) {
        super(true);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.identity = requireNonNull(identity);
        this.sender = requireNonNull(sender);
        this.password = requireNonNull(password);
        this.ackInterval = Duration.ofMillis(requirePositive(ackInterval));
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        if (!sender.equals(ch.remoteAddress())) {
            LOG.debug("Close channel for peer `{}` that is not the wormhole sender.", ch.remoteAddress());
            ch.close();
            return;
        }

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
        ch.pipeline().addLast(new GoBackNArqSenderHandler(ARQ_WINDOW_SIZE, Duration.ofMillis(ARQ_RETRY_TIMEOUT)));
        ch.pipeline().addLast(new GoBackNArqReceiverHandler(ackInterval));
        ch.pipeline().addLast(new ByteToGoBackNArqDataCodec());

        // (de)serializer for WormholeMessages
        ch.pipeline().addLast(new JacksonCodec<>(WormholeMessage.class));

        p.addLast(new WormholeReceiver(out, password));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        new Exception("The sender did not respond within " + handshakeTimeout.toMillis() + "ms. Try again later.", cause).printStackTrace(err);
        ctx.channel().close();
        exitCode.trySet(1);
    }
}
