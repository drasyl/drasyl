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
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

import java.net.SocketAddress;

/**
 * {@link ChannelDuplexHandler} which allows to explicit only handle a specific type of messages.
 * <p>
 * For example here is an implementation which only handle {@link String} messages.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link SimpleChannelDuplexHandler}&lt;{@link String}, {@link String}&gt; {
 *
 *         {@code @Override}
 *         protected void channelRead0({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             System.out.println(message);
 *         }
 *
 *         {@code @Override}
 *         protected void channelWrite0({@link ChannelHandlerContext} ctx, {@link String} message)
 *                   throws {@link Exception} {
 *               System.out.println(message);
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that depending of the constructor parameters it will release all handled messages by
 * passing them to {@link ReferenceCountUtil#release(Object)} and fulfills the corresponding {@link
 * ChannelPromise} in the {@link #channelWrite0} method. In this case you may need to use {@link
 * ReferenceCountUtil#retain(Object)} if you pass the object to the next handler in the {@link
 * ChannelPipeline}.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public abstract class SimpleChannelDuplexHandler<I, O> extends SimpleChannelInboundHandler<I> implements ChannelOutboundHandler {
    private final TypeParameterMatcher outboundMatcher;
    private final boolean outboundAutoRelease;
    private final boolean autoFulfillPromise;

    /**
     * see {@link #SimpleChannelDuplexHandler(boolean, boolean, boolean)} with {@code true} as
     * boolean parameters.
     */
    protected SimpleChannelDuplexHandler() {
        this(true, true, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the types parameter
     * of the class.
     *
     * @param inboundAutoRelease  {@code true} if inbound handled messages should be released
     *                            automatically by passing them to {@link ReferenceCountUtil#release(Object)}.
     * @param outboundAutoRelease {@code true} if outbound handled messages should be released
     *                            automatically by passing them to {@link ReferenceCountUtil#release(Object)}.
     * @param autoFulfillPromise  {@code true} if outbound {@link ChannelPromise} should be
     *                            fulfilled automatically
     */
    protected SimpleChannelDuplexHandler(final boolean inboundAutoRelease,
                                         final boolean outboundAutoRelease,
                                         final boolean autoFulfillPromise) {
        super(inboundAutoRelease);
        this.outboundAutoRelease = outboundAutoRelease;
        this.autoFulfillPromise = autoFulfillPromise;
        this.outboundMatcher = TypeParameterMatcher.find(this, SimpleChannelDuplexHandler.class, "O");
    }

    /**
     * see {@link #SimpleChannelDuplexHandler(Class, Class, boolean, boolean, boolean)} with {@code
     * true} as boolean values.
     */
    protected SimpleChannelDuplexHandler(final Class<? extends I> inboundMessageType,
                                         final Class<? extends O> outboundMessageType) {
        this(inboundMessageType, outboundMessageType, true, true, true);
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType  The type of messages to match
     * @param outboundMessageType The type of messages to match
     * @param inboundAutoRelease  {@code true} if inbound handled messages should be released
     *                            automatically by passing them to {@link ReferenceCountUtil#release(Object)}.
     * @param outboundAutoRelease {@code true} if outbound handled messages should be released
     *                            automatically by passing them to {@link ReferenceCountUtil#release(Object)}.
     * @param autoFulfillPromise  {@code true} if outbound {@link ChannelPromise} should be
     *                            fulfilled automatically
     */
    protected SimpleChannelDuplexHandler(final Class<? extends I> inboundMessageType,
                                         final Class<? extends O> outboundMessageType,
                                         final boolean inboundAutoRelease,
                                         final boolean outboundAutoRelease,
                                         final boolean autoFulfillPromise) {
        super(inboundMessageType, inboundAutoRelease);
        this.outboundAutoRelease = outboundAutoRelease;
        this.autoFulfillPromise = autoFulfillPromise;
        this.outboundMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    /**
     * Is called for each message of type {@link I} on inbound channel.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link SimpleChannelDuplexHandler}
     *            belongs to
     * @param msg the message to handle
     * @throws Exception is thrown if an error occurred
     */
    protected abstract void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception;

    // -----------------------------------------------------------------------------------------------------------------
    // The following code skips this handler and passes every request to the next handler in the pipeline.
    // -----------------------------------------------------------------------------------------------------------------
    @Override
    public void bind(final ChannelHandlerContext ctx, final SocketAddress localAddress,
                     final ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(final ChannelHandlerContext ctx,
                        final SocketAddress remoteAddress,
                        final SocketAddress localAddress,
                        final ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx, final ChannelPromise promise)
            throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(final ChannelHandlerContext ctx,
                           final ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(final ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        boolean release = true;
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked") final O omsg = (O) msg;
                channelWrite0(ctx, omsg, promise);
            }
            else {
                release = false;
                ctx.write(msg, promise);
            }
        }
        finally {
            // Discarding and releasing outbound data ...
            if (outboundAutoRelease && release) {
                ReferenceCountUtil.release(msg);
                if (autoFulfillPromise) {
                    // ... and notify the ChannelPromise
                    promise.setSuccess();
                }
            }
        }
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(final Object msg) {
        return outboundMatcher.match(msg);
    }

    /**
     * Is called for each message of type {@link O} on the outbound channel.
     *
     * @param ctx     the {@link ChannelHandlerContext} which this {@link SimpleChannelDuplexHandler}
     *                belongs to
     * @param msg     the message to handle
     * @param promise the corresponding promise
     * @throws Exception is thrown if an error occurred
     */
    protected abstract void channelWrite0(ChannelHandlerContext ctx,
                                          O msg,
                                          ChannelPromise promise) throws Exception; //NOSONAR

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
    // -----------------------------------------------------------------------------------------------------------------
}