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
package org.drasyl.core.server.actions.messages;

import org.drasyl.core.common.messages.Pong;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.ServerSession;

public class ServerActionPong extends Pong implements ServerAction {
    @Override
    public void onMessage(ServerSession session, NodeServer nodeServer) {
        // Do nothing, netty handles this message
    }

    @Override
    public void onResponse(String responseMsgID, ServerSession session, NodeServer nodeServer) {
        // Do nothing, netty handles this message
    }
}
