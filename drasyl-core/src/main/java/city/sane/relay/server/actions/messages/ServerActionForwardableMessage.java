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

package city.sane.relay.server.actions.messages;

import city.sane.relay.common.filters.ClientFilter;
import city.sane.relay.common.messages.ForwardableMessage;
import city.sane.relay.common.messages.Response;
import city.sane.relay.common.messages.Status;
import city.sane.relay.common.models.Pair;
import city.sane.relay.common.models.SessionUID;
import city.sane.relay.server.RelayServer;
import city.sane.relay.server.actions.ServerAction;
import city.sane.relay.server.session.Session;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ServerActionForwardableMessage extends ForwardableMessage implements ServerAction {

    ServerActionForwardableMessage() {
        super();
    }

    ServerActionForwardableMessage(SessionUID senderUID, SessionUID receiverUID, byte[] blob) {
        super(senderUID, receiverUID, blob);
    }

    @Override
    public void onMessage(Session client, RelayServer relay) {
        Objects.requireNonNull(client);
        Objects.requireNonNull(relay);

        Status feedback;
        SessionUID sender = this.getSenderUID();
        SessionUID receiver = this.getReceiverUID();

        Pair<Map<SessionUID, Session>, Set<SessionUID>> clients = relay.getClientBucket()
                .aggregateChannelMembers(sender);
        Map<SessionUID, Session> localClientUIDs = Maps.newHashMap(clients.getLeft());
        Set<SessionUID> remoteClientUIDs = Sets.newHashSet(clients.getRight());
        Set<SessionUID> filteredClientUIDs = ClientFilter.filter(sender, receiver,
                Sets.union(localClientUIDs.keySet(), remoteClientUIDs));

        localClientUIDs.keySet().retainAll(filteredClientUIDs);
        remoteClientUIDs.retainAll(filteredClientUIDs);

        if (!localClientUIDs.isEmpty()) {
            relay.broadcastMessageLocally(this, localClientUIDs);
            feedback = Status.OK;
        } else if (filteredClientUIDs.size() == 1) {
            SessionUID clientUID = filteredClientUIDs.iterator().next();
            if (remoteClientUIDs.contains(clientUID)) {
                feedback = Status.OK;
            } else {
                feedback = Status.NOT_FOUND;
            }
        } else {
            feedback = Status.NOT_FOUND;
        }

        client.sendMessage(new Response<>(feedback, this.getMessageID()));
    }

    @Override
    public void onResponse(String responseMsgID, Session client, RelayServer relay) {
        // This message does not comes as response to the relay server
    }
}
