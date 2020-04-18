/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.handler;

import io.netty.channel.*;
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
 * Be aware that depending of the constructor parameters it will release all handled messages by passing them to
 * {@link ReferenceCountUtil#release(Object)}. In this case you may need to use
 * {@link ReferenceCountUtil#retain(Object)} if you pass the object to the next handler in the {@link ChannelPipeline}.
 */
public abstract class SimpleChannelDuplexHandler<I, O> extends SimpleChannelInboundHandler<I> implements ChannelOutboundHandler {
    private final TypeParameterMatcher outboundMatcher;
    private final boolean outboundAutoRelease;

    /**
     * see {@link #SimpleChannelDuplexHandler(boolean, boolean)} with {@code true} as boolean parameters.
     */
    protected SimpleChannelDuplexHandler() {
        this(true, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the types parameter of the class.
     *
     * @param inboundAutoRelease  {@code true} if inbound handled messages should be released automatically by passing
     *                            them to
     *                            {@link ReferenceCountUtil#release(Object)}.
     * @param outboundAutoRelease {@code true} if outbound handled messages should be released automatically by passing
     *                            them to
     *                            {@link ReferenceCountUtil#release(Object)}.
     */
    protected SimpleChannelDuplexHandler(boolean inboundAutoRelease, boolean outboundAutoRelease) {
        super(inboundAutoRelease);
        this.outboundAutoRelease = outboundAutoRelease;
        this.outboundMatcher = TypeParameterMatcher.find(this, SimpleChannelDuplexHandler.class, "O");
    }

    /**
     * see {@link #SimpleChannelDuplexHandler(Class, Class, boolean, boolean)} with {@code true} as boolean values.
     */
    protected SimpleChannelDuplexHandler(Class<? extends I> inboundMessageType,
                                         Class<? extends O> outboundMessageType) {
        this(inboundMessageType, outboundMessageType, true, true);
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType  The type of messages to match
     * @param outboundMessageType The type of messages to match
     * @param inboundAutoRelease  {@code true} if inbound handled messages should be released automatically by passing
     *                            them to
     *                            {@link ReferenceCountUtil#release(Object)}.
     * @param outboundAutoRelease {@code true} if outbound handled messages should be released automatically by passing
     *                            them to
     *                            {@link ReferenceCountUtil#release(Object)}.
     */
    protected SimpleChannelDuplexHandler(Class<? extends I> inboundMessageType,
                                         Class<? extends O> outboundMessageType, boolean inboundAutoRelease, boolean outboundAutoRelease) {
        super(inboundMessageType, inboundAutoRelease);
        this.outboundAutoRelease = outboundAutoRelease;
        this.outboundMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) {
        return outboundMatcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        boolean release = true;
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                O omsg = (O) msg;
                channelWrite0(ctx, omsg);
            } else {
                release = false;
                ctx.write(msg, promise);
            }
        } finally {
            if (outboundAutoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    /**
     * Is called for each message of type {@link O} on outbound channel.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link SimpleChannelDuplexHandler}
     *            belongs to
     * @param msg the message to handle
     * @throws Exception is thrown if an error occurred
     */
    protected abstract void channelWrite0(ChannelHandlerContext ctx, O msg) throws Exception; //NOSONAR

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
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                     ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
    // -----------------------------------------------------------------------------------------------------------------
}
