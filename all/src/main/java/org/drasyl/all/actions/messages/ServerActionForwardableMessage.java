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

import org.drasyl.all.filters.ClientFilter;
import org.drasyl.all.messages.ForwardableMessage;
import org.drasyl.all.messages.Response;
import org.drasyl.all.messages.Status;
import org.drasyl.all.models.Pair;
import org.drasyl.all.models.SessionUID;
import org.drasyl.all.Drasyl;
import org.drasyl.all.actions.ServerAction;
import org.drasyl.all.session.Session;
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
    public void onMessage(Session client, Drasyl relay) {
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
    public void onResponse(String responseMsgID, Session client, Drasyl relay) {
        // This message does not comes as response to the relay server
    }
}
