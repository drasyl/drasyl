/*
 * Copyright (c) 2021.
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

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

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
     * <p>
     * Note: It is guaranteed that this method will always be executed inside the {@link
     * #dependentScheduler()}.
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
     * <p>
     * Note: It is guaranteed that this method will always be executed inside the {@link
     * #dependentScheduler()}.
     * <p>
     * If an exception occurs during the execution of this method, the given {@code msg} is
     * automatically released when it is of type {@link io.netty.util.ReferenceCounted}.
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
     * <p>
     * Note: It is guaranteed that this method will always be executed inside the {@link
     * #dependentScheduler()}.
     *
     * @param event  the event
     * @param future the future of the message
     */
    @SuppressWarnings("UnusedReturnValue")
    CompletableFuture<Void> fireEventTriggered(Event event, CompletableFuture<Void> future);

    /**
     * Request to write a message via this {@link HandlerContext} through the {@link Pipeline}.
     * <p>
     * This will result in having the  {@link Handler#write(HandlerContext, Address, Object,
     * CompletableFuture)} method  called of the next  {@link Handler} contained in the  {@link
     * Pipeline}.
     * <p>
     * Note: It is guaranteed that this method will always be executed inside the {@link
     * #dependentScheduler()}.
     * <p>
     * If an exception occurs during the execution of this method, the given {@code msg} is
     * automatically released when it is of type {@link io.netty.util.ReferenceCounted}.
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
     * <i>Implementation Note: This method must always return a scheduler, that differs from the
     * normal pipeline scheduler. E.g. the {@link DrasylSchedulerUtil#getInstanceHeavy()}</i>
     * <p>
     * This method returns an <strong>independent</strong> scheduler that does <strong>not</strong>
     * add the given task to the same pool as the normal pipeline thread pool.
     *
     * @return independent scheduler {@link DrasylScheduler}
     */
    DrasylScheduler independentScheduler();

    /**
     * This method returns the same thread pool that is used by the normal pipeline processing.
     * Tasks given to this scheduler should be short living.
     *
     * @return normal pipeline processing thread pool
     */
    DrasylScheduler dependentScheduler();

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
     * Returns the inbound {@link Serialization} of this pipeline.
     *
     * @return the inbound {@link Serialization}
     */
    Serialization inboundSerialization();

    /**
     * Returns the outbound {@link Serialization} of this pipeline.
     *
     * @return the outbound {@link Serialization}
     */
    Serialization outboundSerialization();
}