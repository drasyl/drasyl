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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndCloseOnExceptionHandler;
import org.drasyl.cli.wormhole.handler.WormholeReceiver;
import org.drasyl.cli.wormhole.message.WormholeMessage;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.handler.crypto.ArmHeaderCodec;
import org.drasyl.node.handler.crypto.LongTimeArmHandler;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormhole.channel.WormholeSendChannelInitializer.MAX_PEERS;
import static org.drasyl.cli.wormhole.channel.WormholeSendChildChannelInitializer.ARM_SESSION_TIME;
import static org.drasyl.cli.wormhole.channel.WormholeSendChildChannelInitializer.ARQ_RETRY_TIMEOUT;

public class WormholeReceiveChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeReceiveChildChannelInitializer.class);
    public static final int REQUEST_TIMEOUT_MILLIS = 10_000;
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Identity identity;
    private final IdentityPublicKey sender;
    private final String password;

    public WormholeReceiveChildChannelInitializer(final PrintStream out,
                                                  final PrintStream err,
                                                  final Worm<Integer> exitCode,
                                                  final Identity identity,
                                                  final IdentityPublicKey sender,
                                                  final String password) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.identity = requireNonNull(identity);
        this.sender = requireNonNull(sender);
        this.password = requireNonNull(password);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws CryptoException {
        if (!sender.equals(ch.remoteAddress())) {
            LOG.debug("Close channel for peer `{}` that is not the wormhole sender.", ch.remoteAddress());
            ch.close();
            return;
        }

        final ChannelPipeline p = ch.pipeline();

        // add ARQ to make sure messages arrive
        ch.pipeline().addLast(new StopAndWaitArqCodec());
        ch.pipeline().addLast(new StopAndWaitArqHandler(ARQ_RETRY_TIMEOUT));
        ch.pipeline().addLast(new ByteToStopAndWaitArqDataCodec());

        p.addLast(new ArmHeaderCodec());
        p.addLast(new LongTimeArmHandler(ARM_SESSION_TIME, MAX_PEERS, identity, (IdentityPublicKey) ch.remoteAddress()));

        // (de)serializer for WormholeMessages
        ch.pipeline().addLast(new JacksonCodec<>(WormholeMessage.class));

        p.addLast(new WormholeReceiver(out, password, REQUEST_TIMEOUT_MILLIS));

        p.addLast(new PrintAndCloseOnExceptionHandler(err, exitCode));

        // close parent as well
        ch.closeFuture().addListener(f -> ch.parent().close());
    }
}
