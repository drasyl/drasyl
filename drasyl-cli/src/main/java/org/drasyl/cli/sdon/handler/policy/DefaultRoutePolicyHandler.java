/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.handler.policy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.cli.sdon.config.DefaultRoutePolicy;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.sdon.config.Policy.PolicyState.ABSENT;

public class DefaultRoutePolicyHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoutePolicyHandler.class);
    private final DefaultRoutePolicy policy;

    public DefaultRoutePolicyHandler(final DefaultRoutePolicy policy) {
        this.policy = requireNonNull(policy);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        policy.setCurrentState(policy.desiredState());
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        policy.setCurrentState(ABSENT);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof OverlayAddressedMessage<?> && ((OverlayAddressedMessage<?>) msg).content() instanceof ByteBuf && ((ByteBuf) ((OverlayAddressedMessage<?>) msg).content()).getInt(0) == 1337) {
            // FIXME
            throw new RuntimeException("route message according to SDN config");
        }
        else {
            ctx.write(msg, promise);
        }
    }
}
