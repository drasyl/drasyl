/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.server.actions.messages;

import org.drasyl.core.common.messages.Join;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.messages.Welcome;
import org.drasyl.core.common.models.SessionChannel;
import org.drasyl.core.common.models.SessionUID;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.Session;

import java.util.Objects;
import java.util.Set;

public class ServerActionJoin extends Join implements ServerAction {
    ServerActionJoin() {
        super();
    }

    ServerActionJoin(SessionUID clientUID, Set<SessionChannel> sessionChannels) {
        super(clientUID, sessionChannels);
    }

    @Override
    public void onMessage(Session client, RelayServer relay) {
        Objects.requireNonNull(client);
        Objects.requireNonNull(relay);

        handleHandoffOrJoin(this, client, relay);
        client.sendMessage(new Response<>(new Welcome(), this.getMessageID()));
    }

    @Override
    public void onResponse(String responseMsgID, Session client, RelayServer relay) {
        // This message does not come as response to the relay server
    }

    private void handleHandoffOrJoin(Join message, Session client, RelayServer relay) {
        SessionUID clientUID = message.getClientUID();

        if (message.getSessionChannels().isEmpty()) {
            relay.getClientBucket().addLocalClientSession(clientUID, client,
                    SessionChannel.of(relay.getConfig().getRelayDefaultChannel()));
        } else {
            relay.getClientBucket().addLocalClientSession(clientUID, client, message.getSessionChannels());
        }
    }
}
