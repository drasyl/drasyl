/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;

/**
 * Joins the Chord circle or will close the {@link io.netty.channel.Channel}.
 */
public class ChordJoinHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ChordJoinHandler.class);
    private final DrasylAddress contact;
    private final LocalChordNode localNode;
    private Future<Void> joinFuture;

    public ChordJoinHandler(final DrasylAddress contact,
                            final LocalChordNode localNode) {
        this.contact = requireNonNull(contact);
        this.localNode = requireNonNull(localNode);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            doJoin(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelJoin();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        doJoin(ctx);

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelJoin();

        ctx.fireChannelInactive();
    }

    private void doJoin(final ChannelHandlerContext ctx) {
        LOG.debug("Try to join Chord circle by asking `{}` to find the successor for my id `{}`.", contact, chordIdHex(ctx.channel().localAddress()));
        joinFuture = localNode.join(contact).addListener((FutureListener<Void>) future -> {
            if (future.isSuccess()) {
                LOG.debug("Joined Chord circle.");
                ctx.pipeline().remove(ctx.name());
            }
            else {
                LOG.error("Failed to join Chord circle `{}`:", contact, future.cause());
                ctx.pipeline().fireExceptionCaught(future.cause());
                ctx.pipeline().close();
            }
        });
    }

    private void cancelJoin() {
        if (joinFuture != null) {
            joinFuture.cancel(false);
            joinFuture = null;
        }
    }
}
