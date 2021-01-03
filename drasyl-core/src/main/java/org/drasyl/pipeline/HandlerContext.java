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
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
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
     * Returns the name of the {@link Handler}.
     *
     * @return the name of the {@link Handler}
     */
    String name();

    /**
     * Returns the associated {@link Handler}.
     *
     * @return the associated {@link Handler}
     */
    Handler handler();

    /**
     * Received an {@link Throwable} in one of the inbound operations.
     * <p>
     * This will result in having the  {@link Handler#exceptionCaught(HandlerContext, Exception)}
     * method  called of the next  {@link Handler} contained in the  {@link Pipeline}.
     *
     * @param cause the cause
     */
    HandlerContext fireExceptionCaught(Exception cause);

    /**
     * Received a message.
     * <p>
     * This will result in having the {@link Handler#read(HandlerContext, Address, Object,
     * CompletableFuture)} method called of the next {@link Handler} contained in the {@link
     * Pipeline}.
     *
     * @param sender the sender of the message
     * @param msg    the message
     * @param future the future of the message
     */
    CompletableFuture<Void> fireRead(Address sender,
                                     Object msg,
                                     CompletableFuture<Void> future);

    /**
     * Received an event.
     * <p>
     * This will result in having the  {@link Handler#eventTriggered(HandlerContext, Event,
     * CompletableFuture)} method  called of the next  {@link Handler} contained in the  {@link
     * Pipeline}.
     *
     * @param event  the event
     * @param future the future of the message
     */
    @SuppressWarnings("UnusedReturnValue")
    CompletableFuture<Void> fireEventTriggered(Event event, CompletableFuture<Void> future);

    /**
     * Request to write a message via this {@link HandlerContext} through the {@link Pipeline}.
     *
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    the future of the message
     */
    CompletableFuture<Void> write(Address recipient,
                                  Object msg,
                                  CompletableFuture<Void> future);

    /**
     * Returns the corresponding {@link DrasylConfig}.
     *
     * @return the corresponding {@link DrasylConfig}
     */
    DrasylConfig config();

    /**
     * Returns the corresponding {@link Pipeline}.
     *
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
     * Returns the identity of this node.
     *
     * @return the identity of this node
     */
    Identity identity();

    /**
     * Returns the peers manager of this node.
     *
     * @return the peers manager of this node
     */
    PeersManager peersManager();

    /**
     * Returns the inbound type validator of this pipeline.
     *
     * @return the inbound type validator
     */
    TypeValidator inboundValidator();

    /**
     * Returns the outbound type validator of this pipeline.
     *
     * @return the outbound type validator
     */
    TypeValidator outboundValidator();
}