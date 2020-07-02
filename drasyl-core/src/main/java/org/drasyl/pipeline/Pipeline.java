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

import java.util.NoSuchElementException;

/**
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
 *  |       [ Messenger.send() ]              [ DrasylNode.send() ]     |
 *  |                                                                   |
 *  |  drasyl Internal I/O                                              |
 *  +-------------------------------------------------------------------+
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
