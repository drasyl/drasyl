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

public interface InboundHandler extends Handler {
    /**
     * Gets called if a {@link ApplicationMessage} was received.
     *
     * @param ctx handler context
     * @param msg the message
     */
    void read(HandlerContext ctx, ApplicationMessage msg);

    /**
     * Gets called if a {@link Event} was emitted.
     *
     * @param ctx   handler context
     * @param event the event
     */
    void eventTriggered(HandlerContext ctx, Event event);

    /**
     * Gets called if a {@link Throwable} was thrown.
     */
    void exceptionCaught(HandlerContext ctx, Throwable cause) throws Exception;
}
