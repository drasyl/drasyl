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
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.drasyl.channel.ConnectionChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.wormhole.handler.WormholeReceiver;
import org.drasyl.cli.wormhole.message.WormholeMessage;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.handler.crypto.ArmHeaderCodec;
import org.drasyl.node.handler.crypto.LongTimeArmHandler;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.wormhole.WormholeCommand.CONNECTION_CONFIG;
import static org.drasyl.cli.wormhole.channel.WormholeSendChannelInitializer.MAX_PEERS;
import static org.drasyl.cli.wormhole.channel.WormholeSendChildChannelInitializer.ARM_SESSION_TIME;

public class WormholeReceiveChildChannelInitializer extends ConnectionChannelInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeReceiveChildChannelInitializer.class);
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey sender;
    private final String password;

    public WormholeReceiveChildChannelInitializer(final PrintStream out,
                                                  final PrintStream err,
                                                  final Worm<Integer> exitCode,
                                                  final IdentityPublicKey sender,
                                                  final String password) {
        super(false, DEFAULT_SERVER_PORT, CONNECTION_CONFIG);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.sender = requireNonNull(sender);
        this.password = requireNonNull(password);
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
        p.addLast(new LongTimeArmHandler(ARM_SESSION_TIME, MAX_PEERS, ch.identity(), (IdentityPublicKey) ch.remoteAddress()));

        super.initChannel(ch);
    }

    @Override
    protected void handshakeCompleted(final ChannelHandlerContext ctx) {
        final ChannelPipeline p = ctx.pipeline();

        p.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        p.addLast(new LengthFieldPrepender(4));

        // (de)serializer for WormholeMessages
        ctx.pipeline().addLast(new JacksonCodec<>(WormholeMessage.class));

        p.addLast(new WormholeReceiver(out, password));

        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        new Exception("The sender did not respond within " + config.userTimeout().toMillis() + "ms. Try again later.", cause).printStackTrace(err);
        ctx.channel().close();
        exitCode.trySet(1);
    }
}
