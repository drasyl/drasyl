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
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@link SimpleInboundHandler} which decodes from one message to one ore more other message(s).
 * <p>
 * For example here is an implementation which decodes an {@link String} to an {@link Integer}.
 *
 * <pre>
 *     public class StringToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link String},{@link Address}&gt; {
 *
 *         {@code @Override}
 *         public void decode({@link HandlerContext} ctx, {@link Address} recipient, {@link String} message, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(message.length());
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed
 * through if they are of type {@link ReferenceCounted}. This is needed as the {@link
 * MessageToMessageDecoder} will call {@link ReferenceCounted#release()} on encoded messages.
 */
@SuppressWarnings("java:S118")
public abstract class MessageToMessageDecoder<I, A extends Address> extends SimpleInboundHandler<I, A> {
    @SuppressWarnings({ "unchecked", "java:S112", "java:S2221" })
    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final A sender,
                                  final I msg,
                                  final CompletableFuture<Void> future) throws Exception {
        final List<Object> out = new ArrayList<>();
        try {
            try {
                decode(ctx, sender, msg, out);
            }
            finally {
                ReferenceCountUtil.safeRelease(msg);
            }

            if (out.isEmpty()) {
                throw new Exception(this.getClass().getSimpleName() + " must produce at least one message.");
            }

            final int size = out.size();
            if (size == 1) {
                ctx.passInbound(sender, out.get(0), future);
            }
            else {
                final FutureCombiner combiner = FutureCombiner.getInstance();

                for (final Object o : out) {
                    combiner.add(ctx.passInbound(sender, o, new CompletableFuture<>()));
                }

                combiner.combine(future);
            }
        }
        catch (final Exception e) {
            future.completeExceptionally(new Exception("Unable to decode message:", e));
        }
    }

    protected abstract void decode(final HandlerContext ctx,
                                   final A recipient,
                                   final I msg,
                                   final List<Object> out) throws IOException;
}
