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

import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.RelayableMessage;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * A list of {@link Handler}s which handles or intercepts inbound events and outbound operations of
 * a {@link org.drasyl.DrasylNode}. {@link Pipeline} implements an advanced form of the
 * <a href="http://www.oracle.com/technetwork/java/interceptingfilter-142169.html">Intercepting
 * Filter</a> pattern to give a user full control over how an event is handled and how the {@link
 * Handler}s in a pipeline interact with each other. This implementation is very closely based on
 * the netty implementation.
 *
 * <h3>Creation of a pipeline</h3>
 * <p>
 * Each per DrasylNode exists one pipeline and it is created automatically when a new node is
 * created.
 *
 * <h3>How an event flows in a pipeline</h3>
 * <p>
 * The following diagram describes how I/O events are processed by {@link Handler}s in a {@link
 * Pipeline} typically. An I/O event is handled by either a {@link InboundHandler} or a {@link
 * OutboundHandler} and be forwarded to its closest handler by calling the event propagation methods
 * defined in {@link HandlerContext}, such as {@link HandlerContext#fireRead(ApplicationMessage)}
 * and {@link HandlerContext#write(ApplicationMessage)}.
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link HandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                            Pipeline               |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  |   HandlerContext.fireIN_EVT()          HandlerContext.OUT_EVT()   |
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ MessageSink.send() ]              [ DrasylNode.send() ]   |
 *  |                                                                   |
 *  |  drasyl Internal I/O                                              |
 *  +-------------------------------------------------------------------+
 *  </pre>
 * <p>
 * An inbound event is handled by the inbound handlers in the bottom-up direction as shown on the
 * left side of the diagram.  An inbound handler usually handles the inbound data generated by the
 * I/O thread on the bottom of the diagram.  The inbound data is often read from a remote peer via
 * the actual input operation such as {@link org.drasyl.messenger.MessageSink#send(RelayableMessage)}.
 * If an inbound event goes beyond the top inbound handler, it is passed to the application.
 * <p>
 * An outbound event is handled by the outbound handler in the top-down direction as shown on the
 * right side of the diagram.  An outbound handler usually generates or transforms the outbound
 * traffic such as write requests. If an outbound event goes beyond the bottom outbound handler, it
 * is handled by the {@link org.drasyl.messenger.Messenger#send(RelayableMessage)} which performs
 * the actual output operation.
 * <p>
 * For example, let us assume that we created the following pipeline:
 * <pre>
 * {@link Pipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Inbound} means it is an inbound
 * handler. The class whose name starts with {@code Outbound} means it is a outbound handler.
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3, 4, 5 when an event
 * goes inbound. When an event goes outbound, the order is 5, 4, 3, 2, 1.  On top of this principle,
 * {@link Pipeline} skips the evaluation of certain handlers to shorten the stack depth:
 * <ul>
 * <li>3 and 4 don't implement {@link InboundHandler}, and therefore the actual evaluation order of an inbound
 *     event will be: 1, 2, and 5.</li>
 * <li>1 and 2 don't implement {@link OutboundHandler}, and therefore the actual evaluation order of a
 *     outbound event will be: 5, 4, and 3.</li>
 * <li>If 5 implements both {@link InboundHandler} and {@link OutboundHandler}, the evaluation order of
 *     an inbound and a outbound event could be 125 and 543 respectively.</li>
 * </ul>
 *
 * <h3>Forwarding an event to the next handler</h3>
 * <p>
 * As you might noticed in the diagram shows, a handler has to invoke the event propagation methods in
 * {@link HandlerContext} to forward an event to its next handler.  Those methods include:
 * <ul>
 * <li>Inbound event propagation methods:
 *     <ul>
 *     <li>{@link HandlerContext#fireRead(ApplicationMessage)}</li>
 *     <li>{@link HandlerContext#fireEventTriggered(Event)}</li>
 *     <li>{@link HandlerContext#fireExceptionCaught(Throwable)}</li>
 *     </ul>
 * </li>
 * <li>Outbound event propagation methods:
 *     <ul>
 *     <li>{@link HandlerContext#write(ApplicationMessage)}</li>
 *     <li>{@link HandlerContext#write(ApplicationMessage, CompletableFuture)}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>
 * A {@link Handler} can be added or removed at any time because a {@link Pipeline} is thread safe.
 *
 * <li>But for every invocation of:
 *       <ul>
 *       <li>{@link Pipeline#executeInbound(ApplicationMessage)}</li>
 *       <li>{@link Pipeline#executeInbound(Event)}</li>
 *       <li>{@link Pipeline#executeOutbound(ApplicationMessage)}</li>
 *       </ul>
 * </li>
 * the invocation is scheduled in the {@link org.drasyl.util.DrasylScheduler}, therefore the order of
 * invocations can't be guaranteed. You have to ensure by yourself, that your handlers are thread-safe
 * if you need it. Also, you have to ensure the order of messages, if you need it.
 */
public interface Pipeline {
    /**
     * Inserts a {@link Handler} at the first position of this pipeline.
     *
     * @param name    the name of the handler to insert first
     * @param handler the handler to insert first
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    Pipeline addFirst(String name, Handler handler);

    /**
     * Appends a {@link Handler} at the last position of this pipeline.
     *
     * @param name    the name of the handler to append
     * @param handler the handler to append
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified handler is {@code null}
     */
    Pipeline addLast(String name, Handler handler);

    /**
     * Inserts a {@link Handler} before an existing handler of this pipeline.
     *
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert before
     * @param handler  the handler to insert before
     * @throws NoSuchElementException   if there's no such entry with the specified {@code
     *                                  baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    Pipeline addBefore(String baseName, String name, Handler handler);

    /**
     * Inserts a {@link Handler} after an existing handler of this pipeline.
     *
     * @param baseName the name of the existing handler
     * @param name     the name of the handler to insert after
     * @param handler  the handler to insert after
     * @throws NoSuchElementException   if there's no such entry with the specified {@code
     *                                  baseName}
     * @throws IllegalArgumentException if there's an entry with the same name already in the
     *                                  pipeline
     * @throws NullPointerException     if the specified baseName or handler is {@code null}
     */
    Pipeline addAfter(String baseName, String name, Handler handler);

    /**
     * Removes the {@link Handler} with the specified name from this pipeline.
     *
     * @param name the name under which the {@link Handler} was stored.
     * @throws NoSuchElementException if there's no such handler with the specified name in this
     *                                pipeline
     * @throws NullPointerException   if the specified name is {@code null}
     */
    Pipeline remove(String name);

    /**
     * Replaces the {@link Handler} of the specified name with a new handler in this pipeline.
     *
     * @param oldName    the name of the {@link Handler} to be replaced
     * @param newName    the name under which the replacement should be added
     * @param newHandler the {@link Handler} which is used as replacement
     * @throws NoSuchElementException   if the handler with the specified old name does not exist in
     *                                  this pipeline
     * @throws IllegalArgumentException if a handler with the specified new name already exists in
     *                                  this pipeline, except for the handler to be replaced
     * @throws NullPointerException     if the specified old handler or new handler is {@code null}
     */
    Pipeline replace(String oldName, String newName, Handler newHandler);

    /**
     * Returns the {@link Handler} with the specified name in this pipeline.
     *
     * @return the handler with the specified name. {@code null} if there's no such handler in this
     * pipeline.
     */
    Handler get(String name);

    /**
     * Returns the context object of the {@link Handler} with the specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name. {@code null} if there's no
     * such handler in this pipeline.
     */
    HandlerContext context(String name);

    void executeInbound(ApplicationMessage msg);

    void executeInbound(Event event);

    void executeOutbound(ApplicationMessage msg);
}
