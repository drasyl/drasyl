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
package org.drasyl.handler.connection;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;

import java.nio.channels.ClosedChannelException;

/**
 * This handler pends all channel writes in a {@link PendingWriteQueue} until a connection handshake
 * has been signaled by receiving a {@link ConnectionHandshakeCompleted} event. Once the handshake
 * is completed, this handler will remove itself from the channel pipeline.
 */
public class ConnectionHandshakePendWritesHandler extends ChannelDuplexHandler {
    private PendingWriteQueue pendingWrites;
    private boolean doFlush;

    ConnectionHandshakePendWritesHandler(final PendingWriteQueue pendingWrites,
                                         final boolean doFlush) {
        this.pendingWrites = pendingWrites;
        this.doFlush = doFlush;
    }

    public ConnectionHandshakePendWritesHandler() {
        this(null, false);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.pendingWrites = new PendingWriteQueue(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (pendingWrites != null) {
            pendingWrites.removeAndFailAll(new Exception(ConnectionHandshakePendWritesHandler.class.getSimpleName() + " that has pend this write has been removed from channel before connection handshake was completed."));
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        // handshake not completed yet, pend write
        pendingWrites.add(msg, promise);
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        doFlush = true;
        ctx.flush();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        // channel is closing, discard all pending writes
        pendingWrites.removeAndFailAll(new ClosedChannelException());
        pendingWrites = null;
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof ConnectionHandshakeCompleted) {
            // handshake completed! perform all pending writes and remove itself from pipeline
            pendingWrites.removeAndWriteAll();
            if (doFlush) {
                ctx.flush();
            }
            ctx.pipeline().remove(this);
        }

        // pass through events
        ctx.fireUserEventTriggered(evt);
    }
}
