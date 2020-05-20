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

package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;

/**
 * This class describes how a client has to respond when receiving a {@link Message} of type
 * <code>T</code>.
 *
 * @param <T>
 */
public interface ClientMessageAction<T extends Message<?>> extends MessageAction<T> {
    /**
     * Describes how the Client <code>superPeerClient</code> should react when a {@link Message} of
     * type
     * <code>T</code> is received from Server in the connection <code>session</code>.
     *
     * @param connection      the super peer connection
     * @param superPeerClient the super peer client
     */
    void onMessageClient(SuperPeerClientConnection connection, SuperPeerClient superPeerClient);
}
