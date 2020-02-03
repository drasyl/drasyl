/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.actions.messages;

import org.drasyl.all.messages.Pong;
import org.drasyl.all.Drasyl;
import org.drasyl.all.actions.ServerAction;
import org.drasyl.all.session.Session;

public class ServerActionPong extends Pong implements ServerAction {
    @Override
    public void onMessage(Session client, Drasyl relay) {
        // Do nothing, netty handles this message
    }

    @Override
    public void onResponse(String responseMsgID, Session client, Drasyl relay) {
        // Do nothing, netty handles this message
    }
}
