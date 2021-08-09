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
package org.drasyl.remote.handler;

import com.typesafe.config.Config;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.PEERS_MANAGER_ATTR_KEY;

/**
 * This handler uses preconfigured static routes ({@link org.drasyl.DrasylConfig#getStaticRoutes(Config,
 * String)}) to deliver messages.
 */
@Sharable
public final class StaticRoutesHandler extends ChannelDuplexHandler {
    public static final StaticRoutesHandler INSTANCE = new StaticRoutesHandler();
    private static final Logger LOG = LoggerFactory.getLogger(StaticRoutesHandler.class);
    private static final Object path = StaticRoutesHandler.class;

    private StaticRoutesHandler() {
        // singleton
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage && ((AddressedMessage<?, ?>) msg).address() instanceof IdentityPublicKey) {
            final ApplicationMessage applicationMsg = (ApplicationMessage) ((AddressedMessage<?, ?>) msg).message();
            final IdentityPublicKey recipient = (IdentityPublicKey) ((AddressedMessage<?, ?>) msg).address();

            final InetSocketAddressWrapper staticAddress = ctx.attr(CONFIG_ATTR_KEY).get().getRemoteStaticRoutes().get(recipient);
            if (staticAddress != null) {
                LOG.trace("Send message `{}` via static route {}.", () -> applicationMsg, () -> staticAddress);
                ctx.writeAndFlush(new AddressedMessage<>(applicationMsg, staticAddress), promise);
            }
            else {
                // passthrough message
                ctx.writeAndFlush(msg, promise);
            }
        }
        else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        populateRoutes(ctx);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        clearRoutes(ctx);

        super.channelInactive(ctx);
    }

    private static synchronized void populateRoutes(final ChannelHandlerContext ctx) {
        ctx.attr(CONFIG_ATTR_KEY).get().getRemoteStaticRoutes().forEach(((publicKey, address) -> ctx.attr(PEERS_MANAGER_ATTR_KEY).get().addPath(ctx, publicKey, path)));
    }

    private static synchronized void clearRoutes(final ChannelHandlerContext ctx) {
        ctx.attr(CONFIG_ATTR_KEY).get().getRemoteStaticRoutes().keySet().forEach(publicKey -> ctx.attr(PEERS_MANAGER_ATTR_KEY).get().removePath(ctx, publicKey, path));
    }
}
