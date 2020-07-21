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
package org.drasyl.pipeline;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylScheduler;

import java.util.concurrent.CompletableFuture;

/**
 * Enables a {@link Handler} to interact with its {@link Pipeline} and other handlers. Among other
 * things a handler can notify the next {@link Handler} in the {@link Pipeline} as well as modify
 * the {@link Pipeline} it belongs to dynamically.
 */
public interface HandlerContext {
    /**
     * @return the name of the {@link Handler}
     */
    String name();

    /**
     * @return the associated {@link Handler}
     */
    Handler handler();

    /**
     * Received an {@link Throwable} in one of the inbound operations.
     * <p>
     * This will result in having the  {@link InboundHandler#exceptionCaught(HandlerContext,
     * Exception)} method  called of the next  {@link InboundHandler} contained in the  {@link
     * Pipeline}.
     *
     * @param cause the cause
     */
    HandlerContext fireExceptionCaught(Exception cause);

    /**
     * Received a message.
     * <p>
     * This will result in having the {@link InboundHandler#read(HandlerContext, Object)} method
     * called of the next {@link InboundHandler} contained in the {@link Pipeline}.
     *
     * @param sender the sender of the message
     * @param msg    the message
     */
    HandlerContext fireRead(CompressedPublicKey sender, Object msg);

    /**
     * Received an event.
     * <p>
     * This will result in having the  {@link InboundHandler#eventTriggered(HandlerContext, Event)}
     * method  called of the next  {@link InboundHandler} contained in the  {@link Pipeline}.
     *
     * @param event the event
     */
    HandlerContext fireEventTriggered(Event event);

    /**
     * Request to write a message via this {@link HandlerContext} through the {@link Pipeline}.
     *
     * @param recipient the recipient of the message
     * @param msg       the message
     */
    CompletableFuture<Void> write(CompressedPublicKey recipient, Object msg);

    /**
     * Request to write a message via this {@link HandlerContext} through the {@link Pipeline}.
     *
     * @param recipient the recipient of the message
     * @param msg       the message
     */
    CompletableFuture<Void> write(CompressedPublicKey recipient,
                                  Object msg,
                                  CompletableFuture<Void> future);

    /**
     * @return the corresponding {@link DrasylConfig}
     */
    DrasylConfig config();

    /**
     * @return the corresponding {@link Pipeline}
     */
    Pipeline pipeline();

    /**
     * <i>Implementation Note: This method should always return a scheduler, that differs from the
     * normal pipeline scheduler. E.g. the {@link DrasylScheduler#getInstanceHeavy()}</i>
     *
     * @return the corresponding {@link Scheduler}
     */
    Scheduler scheduler();

    /**
     * @return the identity of this node
     */
    Identity identity();

    /**
     * @return the type validator
     */
    TypeValidator validator();
}
