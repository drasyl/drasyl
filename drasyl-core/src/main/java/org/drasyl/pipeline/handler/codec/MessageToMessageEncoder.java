/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.pipeline.handler.codec;

import io.netty.util.ReferenceCounted;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.ReferenceCountUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@link SimpleOutboundHandler} which encodes from one message to one ore more other message(s).
 * <p>
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>
 *     public class IntegerToStringEncoder extends
 *             {@link MessageToMessageEncoder}&lt;{@link Integer},{@link Address}&gt; {
 *
 *         {@code @Override}
 *         public void encode({@link HandlerContext} ctx, {@link Address} recipient, {@link Integer} message, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(message.toString());
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed
 * through if they are of type {@link ReferenceCounted}. This is needed as the {@link
 * MessageToMessageEncoder} will call {@link ReferenceCounted#release()} on encoded messages.
 */
@SuppressWarnings("java:S118")
public abstract class MessageToMessageEncoder<O, A extends Address> extends SimpleOutboundHandler<O, A> {
    @SuppressWarnings({ "java:S112", "java:S2221" })
    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final CompletableFuture<Void> future) throws Exception {
        final List<Object> out = new ArrayList<>();
        try {
            try {
                encode(ctx, recipient, msg, out);
            }
            finally {
                ReferenceCountUtil.safeRelease(msg);
            }

            if (out.isEmpty()) {
                throw new Exception(this.getClass().getSimpleName() + " must produce at least one message.");
            }

            final int size = out.size();
            if (size == 1) {
                ctx.passOutbound(recipient, out.get(0), future);
            }
            else {
                final FutureCombiner combiner = FutureCombiner.getInstance();

                for (final Object o : out) {
                    combiner.add(ctx.passOutbound(recipient, o, new CompletableFuture<>()));
                }

                combiner.combine(future);
            }
        }
        catch (final Exception e) {
            future.completeExceptionally(new Exception("Unable to encode message:", e));
        }
    }

    /**
     * Encode from one message to one or more other. This method will be called for each outbound
     * message that can be handled by this decoder.
     *
     * @param ctx       the {@link HandlerContext} which this {@link MessageToMessageDecoder}
     *                  belongs to
     * @param recipient the recipient of the message
     * @param msg       the message to encode
     * @param out       the {@link List} to which encoded messages should be added
     * @throws Exception is thrown if an error occurs
     */
    @SuppressWarnings("java:S112")
    protected abstract void encode(final HandlerContext ctx,
                                   final A recipient,
                                   final O msg,
                                   final List<Object> out) throws Exception;
}
