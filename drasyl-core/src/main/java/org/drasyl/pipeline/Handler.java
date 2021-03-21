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
