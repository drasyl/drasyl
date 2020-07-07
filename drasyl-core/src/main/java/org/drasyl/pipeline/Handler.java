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

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in its
 * {@link Pipeline}.
 *
 * <h3>Sub-types</h3>
 * <p>
 * {@link Handler} itself does not provide many methods, but you usually have to implement one of
 * its subtypes:
 * <ul>
 * <li>{@link InboundHandler} to handle inbound I/O events, and</li>
 * <li>{@link OutboundHandler} to handle outbound I/O operations.</li>
 * </ul>
 * </p>
 * <p>
 * Alternatively, the following adapter classes are provided for your convenience:
 * <ul>
 * <li>{@link InboundHandlerAdapter} to handle inbound I/O events,</li>
 * <li>{@link OutboundHandlerAdapter} to handle outbound I/O operations, and</li>
 * <li>{@link DuplexHandler} to handle both inbound and outbound events</li>
 * </ul>
 * </p>
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link Handler} is provided with a {@link HandlerContext}
 * object.  A {@link Handler} is supposed to interact with the
 * {@link Pipeline} it belongs to via a context object.  Using the
 * context object, the {@link Handler} can pass events upstream or
 * downstream or modify the pipeline dynamically.
 */
public interface Handler {
    /**
     * Gets called after the {@link Handler} was added to the actual context and it's ready to
     * handle events.
     */
    void handlerAdded(HandlerContext ctx);

    /**
     * Gets called after the {@link Handler} was removed from the actual context and it doesn't
     * handle events anymore.
     */
    void handlerRemoved(HandlerContext ctx);
}
