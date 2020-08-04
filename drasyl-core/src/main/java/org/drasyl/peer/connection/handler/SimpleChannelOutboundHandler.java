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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelOutboundHandlerAdapter} which allows to explicit only handle a specific type of
 * messages.
 * <p>
 * For example here is an implementation which only handle {@link String} messages.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link SimpleChannelOutboundHandler}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         protected void channelWrite0({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             System.out.println(message);
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that depending of the constructor parameters it will release all handled messages by
 * passing them to {@link ReferenceCountUtil#release(Object)}. In this case you may need to use
 * {@link ReferenceCountUtil#retain(Object)} if you pass the object to the next handler in the
 * {@link ChannelPipeline}.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public abstract class SimpleChannelOutboundHandler<O> extends ChannelOutboundHandlerAdapter {
    private final TypeParameterMatcher outboundMatcher;
    private final boolean outboundAutoRelease;
    private final boolean autoFulfillPromise;

    /**
     * see {@link #SimpleChannelOutboundHandler(boolean, boolean)} with {@code false} as boolean
     * parameters.
     */
    protected SimpleChannelOutboundHandler() {
        this(false, false);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     *
     * @param outboundAutoRelease {@code true} if handled messages should be released automatically
     *                            by passing them to {@link ReferenceCountUtil#release(Object)}.
     * @param autoFulfillPromise  {@code true} if handled promise should be released automatically
     *                            by calling {@link ChannelPromise#setSuccess()}.
     */
    protected SimpleChannelOutboundHandler(boolean outboundAutoRelease,
                                           boolean autoFulfillPromise) {
        this.outboundAutoRelease = outboundAutoRelease;
        this.autoFulfillPromise = autoFulfillPromise;
        this.outboundMatcher = TypeParameterMatcher.find(this, SimpleChannelOutboundHandler.class, "O");
    }

    /**
     * see {@link #SimpleChannelOutboundHandler(Class, boolean, boolean)} with {@code false} as
     * boolean parameters.
     */
    protected SimpleChannelOutboundHandler(Class<? extends O> outboundMessageType) {
        this(outboundMessageType, false, false);
    }

    /**
     * Create a new instance.
     *
     * @param outboundMessageType type of messages
     * @param outboundAutoRelease {@code true} if handled messages should be released automatically
     *                            by passing them to {@link ReferenceCountUtil#release(Object)}.
     * @param autoFulfillPromise  {@code true} if handled promise should be released automatically
     *                            by calling {@link ChannelPromise#setSuccess()}.
     */
    protected SimpleChannelOutboundHandler(Class<? extends O> outboundMessageType,
                                           boolean outboundAutoRelease,
                                           boolean autoFulfillPromise) {
        this.outboundAutoRelease = outboundAutoRelease;
        this.autoFulfillPromise = autoFulfillPromise;
        this.outboundMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    @Override
    public void write(ChannelHandlerContext ctx,
                      Object msg,
                      ChannelPromise promise) throws Exception {
        boolean release = true;
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                O omsg = (O) msg;
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
    public boolean acceptOutboundMessage(Object msg) {
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
}