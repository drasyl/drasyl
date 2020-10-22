/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ExceptionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_FORMAT;
import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_INTERNAL;

/**
 * This handler listens to exceptions on the pipeline and sends them as {@link ExceptionMessage} to
 * the peer.
 */
public class ExceptionHandler extends ChannelDuplexHandler {
    public static final String EXCEPTION_HANDLER = "exceptionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);
    private final Identity identity;
    private final ChannelExceptionListener exceptionListener;
    private final boolean rethrowExceptions;
    Throwable handledCause;

    ExceptionHandler(final Identity identity,
                     final ChannelExceptionListener exceptionListener,
                     final Throwable handledCause,
                     final boolean rethrowExceptions) {
        this.identity = identity;
        this.exceptionListener = exceptionListener;
        this.handledCause = handledCause;
        this.rethrowExceptions = rethrowExceptions;
    }

    /**
     * Exception handler that does not re-throw occurred {@link Exception}s on {@link
     * #exceptionCaught} to the next pipeline.
     *
     * @param identity node's identity
     */
    public ExceptionHandler(final Identity identity) {
        this(identity, false);
    }

    /**
     * Exception handler that does re-throw occurred {@link Exception}s on {@link #exceptionCaught}
     * to the next pipeline, if {@code rethrowExceptions} is {@code true}.
     *
     * @param identity          node's identity
     * @param rethrowExceptions if {@code true} re-throws to next channel in the pipeline
     */
    public ExceptionHandler(final Identity identity, final boolean rethrowExceptions) {
        this.identity = identity;
        this.exceptionListener = new ChannelExceptionListener();
        this.handledCause = null;
        this.rethrowExceptions = rethrowExceptions;
    }

    @Override
    public void bind(final ChannelHandlerContext ctx, final SocketAddress localAddress,
                     final ChannelPromise promise) {
        ctx.bind(localAddress, exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void connect(final ChannelHandlerContext ctx,
                        final SocketAddress remoteAddress,
                        final SocketAddress localAddress,
                        final ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        ctx.disconnect(exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        ctx.close(exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void deregister(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        ctx.deregister(exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        ctx.write(msg, exceptionListener.getListener(promise, ctx));
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        sendException(ctx, cause);

        if (rethrowExceptions) {
            ctx.fireExceptionCaught(cause);
        }
    }

    /**
     * Writes an exception to the outbound channel and takes care of duplications.
     *
     * @param ctx the channel handler context
     * @param e   the exception
     */
    private void sendException(final ChannelHandlerContext ctx, final Throwable e) {
        if (e instanceof ClosedChannelException
                || handledCause != null && Objects.equals(handledCause.getMessage(), e.getMessage()) || Objects.equals("SSLEngine closed already", e.getMessage())) {
            return;
        }

        handledCause = e;

        if (ctx.channel().isWritable()) {
            final ExceptionMessage msg;
            if (e instanceof DecoderException) {
                msg = new ExceptionMessage(identity.getPublicKey(), identity.getProofOfWork(), ERROR_FORMAT);
            }
            else {
                msg = new ExceptionMessage(identity.getPublicKey(), identity.getProofOfWork(), ERROR_INTERNAL);
            }
            ctx.writeAndFlush(msg);
        }
        LOG.debug("Exception caught: {}", e.getMessage());
    }

    class ChannelExceptionListener {
        ChannelPromise getListener(final ChannelPromise promise, final ChannelHandlerContext ctx) {
            return promise.addListener(future -> {
                if (!future.isSuccess()) {
                    sendException(ctx, future.cause());
                }
            });
        }
    }
}