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

import org.drasyl.all.handler.codec.message.MessageDecoder;
import org.drasyl.all.handler.codec.message.MessageEncoder;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Creates a newly configured {@link ChannelPipeline} for a new channel for a Session to or from a relay server.
 *
 * <p>
 * <b>Note: You have to add a websocket handler by yourself to the {@link #beforeMarshalStage(ChannelPipeline)}</b>
 * </p>
 */
public abstract class DefaultSessionInitializer extends ChannelInitializer<SocketChannel> {
    private final int flushBufferSize;
    private final Duration readIdleTimeout;
    private final int pingPongRetries;

    protected DefaultSessionInitializer(int flushBufferSize, Duration readIdleTimeout, int pingPongRetries) {
        this.flushBufferSize = flushBufferSize;
        this.readIdleTimeout = readIdleTimeout;
        this.pingPongRetries = pingPongRetries;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        beforeSslStage(ch);
        sslStage(ch);
        afterSslStage(ch);

        beforeBufferStage(pipeline);
        bufferStage(pipeline);
        afterBufferStage(pipeline);

        beforeMarshalStage(pipeline);
        marshalStage(pipeline);
        afterMarshalStage(pipeline);

        beforeFilterStage(pipeline);
        filterStage(pipeline);
        afterFilterStage(pipeline);

        beforePojoMarshalStage(pipeline);
        pojoMarshalStage(pipeline);
        afterPojoMarshalStage(pipeline);

        beforeIdleStage(pipeline);
        idleStage(pipeline);
        afterIdleStage(pipeline);

        customStage(pipeline);

        beforeExceptionStage(pipeline);
        exceptionStage(pipeline);
        afterExceptionStage(pipeline);
    }

    /**
     * At this stage the {@link SslHandler} is added to the pipeline.
     *
     * @param ch the {@link SocketChannel}
     */
    protected void sslStage(SocketChannel ch) {
        SslHandler sslHandler = generateSslContext(ch);

        if (sslHandler != null) {
            ch.pipeline().addLast("sslHandler", sslHandler);
        }
    }

    /**
     * Adds {@link ChannelHandler} for buffer strategies to the {@link ChannelPipeline}.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void bufferStage(ChannelPipeline pipeline) {
        // Use buffer for better IO performance
        pipeline.addLast(
                new FlushConsolidationHandler(Math.max(1, flushBufferSize), true));
    }

    /**
     * Adds {@link ChannelHandler} for marshalling to the {@link ChannelPipeline}.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void marshalStage(ChannelPipeline pipeline) {

    }

    /**
     * Adds {@link ChannelHandler} for filtering to the {@link ChannelPipeline}.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void filterStage(ChannelPipeline pipeline) {
    }

    /**
     * Adds {@link ChannelHandler} for POJO marshalling handling to the {@link ChannelPipeline}.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void pojoMarshalStage(ChannelPipeline pipeline) {
        // From String to Message
        pipeline.addLast("messageDecoder", MessageDecoder.INSTANCE);
        pipeline.addLast("messageEncoder", MessageEncoder.INSTANCE);
    }

    /**
     * Adds {@link ChannelHandler} for idle handling to the {@link ChannelPipeline}.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void idleStage(ChannelPipeline pipeline) {
        // Add handler to emit idle event for ping/pong requests
        if (!readIdleTimeout.isZero())
            pipeline.addLast("idleEvent", new IdleStateHandler(readIdleTimeout.toMillis(), 0, 0, TimeUnit.MILLISECONDS));
        pipeline.addLast("pingPongHandler", new PingPongHandler(pingPongRetries));
    }

    /**
     * Generates a {@link SslHandler} that can be added to the {@link ChannelPipeline}.
     * If this method returns {@code null}, the SslHandler is not added to the {@link ChannelPipeline}.
     *
     * @param ch the {@link SocketChannel} to initialize a {@link SslHandler}
     * @return {@link SslHandler} or {@code null}
     */
    protected abstract SslHandler generateSslContext(SocketChannel ch);

    /**
     * Adds {@link ChannelHandler} to the {@link ChannelPipeline}, after the default initialization has taken place.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected abstract void customStage(ChannelPipeline pipeline);

    /**
     * Adds {@link ChannelHandler} for exception handling to the {@link ChannelPipeline}.
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void exceptionStage(ChannelPipeline pipeline) {
    }

    protected void beforeSslStage(SocketChannel ch) {
    }

    protected void beforeBufferStage(ChannelPipeline pipeline) {
    }

    protected abstract void beforeMarshalStage(ChannelPipeline pipeline);

    protected void beforeFilterStage(ChannelPipeline pipeline) {
    }

    protected void beforeIdleStage(ChannelPipeline pipeline) {
    }

    protected void beforePojoMarshalStage(ChannelPipeline pipeline) {
    }

    protected void beforeExceptionStage(ChannelPipeline pipeline) {
    }

    protected void afterSslStage(SocketChannel ch) {

    }

    protected void afterBufferStage(ChannelPipeline pipeline) {
    }

    protected void afterMarshalStage(ChannelPipeline pipeline) {
    }

    protected void afterFilterStage(ChannelPipeline pipeline) {
    }

    protected void afterIdleStage(ChannelPipeline pipeline) {
    }

    protected void afterPojoMarshalStage(ChannelPipeline pipeline) {
    }

    /**
     * Adds {@link ChannelHandler} to the {@link ChannelPipeline}, after the default {@link ExceptionHandler} has
     * taken place.
     *
     * <p>If you want to handle an exception at this place, you have to set {@link #rethrowExceptions} to {@code true}.
     * </p>
     *
     * @param pipeline the {@link ChannelPipeline}
     */
    protected void afterExceptionStage(ChannelPipeline pipeline) {
    }
}
