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
package org.drasyl.remote.handler.portmapper;

import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.remote.protocol.AddressedByteBuf;

/**
 * Represents a method for creating port forwarding (e.g., PCP, NAT-PMP, or UPnP-IGD).
 */
public interface PortMapping {
    /**
     * Tells the method to create a port forwarding and renew it independently. If no forwarding can
     * be created, {@code onFailure} must be called once.
     *
     * @param ctx       the handler context
     * @param event     the node up event
     * @param onFailure will be called once on failure
     */
    void start(HandlerContext ctx, NodeUpEvent event, Runnable onFailure);

    /**
     * Shall remove any existing port forwarding again.
     *
     * @param ctx the handler context
     */
    void stop(HandlerContext ctx);

    /**
     * Is called for incoming messages and thus enables this method to react to relevant messages.
     *
     * @param ctx the handler context
     * @param msg the message
     */
    void handleMessage(HandlerContext ctx, AddressedByteBuf msg);

    /**
     * Is called for incoming messages and returns true if the message should be consumed and
     * removed from the pipeline.
     *
     * @param msg the message
     * @return {@code true} if the message is relevant for the current port forwarding method.
     * Otherwise {@code false}
     */
    boolean acceptMessage(AddressedByteBuf msg);
}
