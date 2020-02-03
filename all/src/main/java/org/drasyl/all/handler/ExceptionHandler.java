/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.handler;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.drasyl.all.messages.RelayException;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * This handler listens to exceptions on the pipeline and throws them as {@link RelayException} to the peer.
 */
public class ExceptionHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);
    private ChannelExceptionListener exceptionListener;
    Throwable handledCause;
    private boolean rethrowExceptions;

    class ChannelExceptionListener {
        ChannelPromise getListener(ChannelPromise promise, ChannelHandlerContext ctx) {
            return promise.addListener(future -> {
                if (!future.isSuccess()) {
                    sendException(ctx, future.cause());
                }
            });
        }
    }

    ExceptionHandler(ChannelExceptionListener exceptionListener, Throwable handledCause,
                     boolean rethrowExceptions) {
        this.exceptionListener = exceptionListener;
        this.handledCause = handledCause;
        this.rethrowExceptions = rethrowExceptions;
    }

    /**
     * Exception handler that does not re-throw occurred {@link Exception}s on
     * {@link #exceptionCaught} to the next pipeline.
     */
    public ExceptionHandler() {
        this(false);
    }

    /**
     * Exception handler that does re-throw occurred {@link Exception}s on
     * {@link #exceptionCaught} to the next pipeline, if {@code rethrowExceptions}
     * is {@code true}.
     *
     * @param rethrowExceptions if {@code true} re-throws to next channel in the pipeline
     */
    public ExceptionHandler(boolean rethrowExceptions) {
        this.exceptionListener = new ChannelExceptionListener();
        this.handledCause = null;
        this.rethrowExceptions = rethrowExceptions;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        sendException(ctx, cause);

        if (rethrowExceptions)
            ctx.fireExceptionCaught(cause);
    }

    /**
     * Writes an exception to the outbound channel and takes care of duplications.
     *
     * @param ctx the channel handler context
     * @param e   the exception
     */
    private void sendException(ChannelHandlerContext ctx, Throwable e) {
        if (e instanceof ClosedChannelException
                || handledCause != null && Objects.equals(handledCause.getMessage(), e.getMessage()))
            return;

        handledCause = e;

        if (ctx.channel().isWritable()) {
            ctx.writeAndFlush(new RelayException(e));
        }
        LOG.debug("", e);
    }
}
