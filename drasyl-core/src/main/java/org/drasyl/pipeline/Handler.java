/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.pipeline;

import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;

import java.util.concurrent.CompletableFuture;

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in its
 * {@link Pipeline}.
 *
 * <h3>Extend {@link HandlerAdapter} instead</h3>
 * <p>
 * Because this interface has many methods to implement, you might want to extend {@link
 * HandlerAdapter} instead.
 * </p>
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link Handler} is provided with a {@link HandlerContext} object.  A {@link Handler} is
 * supposed to interact with the {@link Pipeline} it belongs to via a context object.  Using the
 * context object, the {@link Handler} can pass events upstream or downstream or modify the pipeline
 * dynamically.
 */
public interface Handler {
    /**
     * Gets called after the {@link Handler} was added to the actual context and it's ready to
     * handle events.
     */
    void onAdded(HandlerContext ctx);

    /**
     * Gets called after the {@link Handler} was removed from the actual context and it doesn't
     * handle events anymore.
     */
    void onRemoved(HandlerContext ctx);

    /**
     * Gets called if a {@link Object} was received.
     *
     * @param ctx    handler context
     * @param sender the sender of the message
     * @param msg    the message
     * @param future a future for the message
     */
    @SuppressWarnings("java:S112")
    void onInbound(HandlerContext ctx,
                   Address sender,
                   Object msg,
                   CompletableFuture<Void> future) throws Exception;

    /**
     * Gets called if a {@link Event} was emitted.
     *
     * @param ctx    handler context
     * @param event  the event
     * @param future a future for the message
     */
    void onEvent(HandlerContext ctx,
                 Event event,
                 CompletableFuture<Void> future);

    /**
     * Gets called if a {@link Exception} was thrown.
     */
    void onException(HandlerContext ctx, Exception cause);

    /**
     * Gets called if a {@link Object} was send from the application to a recipient.
     *
     * @param ctx       handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    a future for the message
     */
    @SuppressWarnings("java:S112")
    void onOutbound(HandlerContext ctx,
                    Address recipient,
                    Object msg,
                    CompletableFuture<Void> future) throws Exception;
}
