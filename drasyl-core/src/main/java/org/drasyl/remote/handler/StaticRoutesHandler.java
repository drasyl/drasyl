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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.AddPathEvent;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.RemovePathEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This handler uses preconfigured static routes ({@link org.drasyl.DrasylConfig#getStaticRoutes(Config,
 * String)}) to deliver messages.
 */
public final class StaticRoutesHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(StaticRoutesHandler.class);
    private static final Object path = StaticRoutesHandler.class;
    private final Map<IdentityPublicKey, SocketAddress> staticRoutes;

    public StaticRoutesHandler(final Map<IdentityPublicKey, SocketAddress> staticRoutes) {
        this.staticRoutes = requireNonNull(staticRoutes);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ApplicationMessage && ((AddressedMessage<?, ?>) msg).address() instanceof IdentityPublicKey) {
            final ApplicationMessage applicationMsg = (ApplicationMessage) ((AddressedMessage<?, ?>) msg).message();
            final IdentityPublicKey recipient = (IdentityPublicKey) ((AddressedMessage<?, ?>) msg).address();

            final SocketAddress staticAddress = staticRoutes.get(recipient);
            if (staticAddress != null) {
                LOG.trace("Send message `{}` via static route {}.", () -> applicationMsg, () -> staticAddress);
                ctx.write(new AddressedMessage<>(applicationMsg, staticAddress), promise);
            }
            else {
                // pass through message
                ctx.write(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        populateRoutes(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        clearRoutes(ctx);

        ctx.fireChannelInactive();
    }

    private void populateRoutes(final ChannelHandlerContext ctx) {
        staticRoutes.forEach(((publicKey, address) -> ctx.fireUserEventTriggered(AddPathEvent.of(publicKey, path))));
    }

    private void clearRoutes(final ChannelHandlerContext ctx) {
        staticRoutes.keySet().forEach(publicKey -> ctx.fireUserEventTriggered(RemovePathEvent.of(publicKey, path)));
    }
}
