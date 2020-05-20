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

import org.drasyl.event.Event;
import org.drasyl.event.EventCode;
import org.drasyl.event.Node;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;

public class WelcomeMessageAction extends AbstractMessageAction<WelcomeMessage> implements ClientMessageAction<WelcomeMessage> {
    public WelcomeMessageAction(WelcomeMessage message) {
        super(message);
    }

    @Override
    public void onMessageClient(SuperPeerClientConnection connection,
                                SuperPeerClient superPeerClient) {
        superPeerClient.getOnEvent().accept(new Event(EventCode.EVENT_NODE_ONLINE, Node.of(superPeerClient.getIdentityManager().getIdentity())));
    }
}
