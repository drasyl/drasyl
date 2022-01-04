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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This handler uses preconfigured static routes to deliver messages.
 */
public final class StaticRoutesHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(StaticRoutesHandler.class);
    private static final Object path = StaticRoutesHandler.class;
    private final Map<DrasylAddress, InetSocketAddress> staticRoutes;

    public StaticRoutesHandler(final Map<DrasylAddress, InetSocketAddress> staticRoutes) {
        this.staticRoutes = requireNonNull(staticRoutes);
    }

    @SuppressWarnings({ "unchecked", "SuspiciousMethodCalls" })
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof ApplicationMessage) {
            final DrasylAddress recipient = ((OverlayAddressedMessage<ApplicationMessage>) msg).recipient();

            final InetSocketAddress staticAddress = staticRoutes.get(recipient);
            if (staticAddress != null) {
                LOG.trace("Resolve message `{}` for peer `{}` to inet address `{}`.", (((OverlayAddressedMessage<ApplicationMessage>) msg).content()).getNonce(), recipient, staticAddress);
                ctx.write(((OverlayAddressedMessage<ApplicationMessage>) msg).resolve(staticAddress), promise);
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
        staticRoutes.forEach(((publicKey, address) -> ctx.fireUserEventTriggered(AddPathEvent.of(publicKey, address, path))));
    }

    private void clearRoutes(final ChannelHandlerContext ctx) {
        staticRoutes.keySet().forEach(publicKey -> ctx.fireUserEventTriggered(RemovePathEvent.of(publicKey, path)));
    }
}
