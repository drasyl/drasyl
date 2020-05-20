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
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerConnection;

/**
 * This class describes how a server has to respond when receiving a {@link Message} of type
 * <code>T</code>.
 *
 * @param <T>
 */
public interface ServerMessageAction<T extends Message<?>> extends MessageAction<T> {
    /**
     * Describes how the Server <code>nodeServer</code> should react when a {@link Message} of type
     * <code>T</code> is received from Client in the connection <code>session</code>.
     *
     * @param session
     * @param nodeServer
     */
    void onMessageServer(NodeServerConnection session, NodeServer nodeServer);
}
