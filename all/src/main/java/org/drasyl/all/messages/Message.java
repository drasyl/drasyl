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

package org.drasyl.all.messages;

import org.drasyl.all.messages.p2p.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Welcome.class, name = "Welcome"),
        @JsonSubTypes.Type(value = ClientsStocktaking.class, name = "ClientsStocktaking"),
        @JsonSubTypes.Type(value = RelayException.class, name = "RelayException"),
        @JsonSubTypes.Type(value = ForwardableMessage.class, name = "ForwardableMessage"),
        @JsonSubTypes.Type(value = Join.class, name = "Join"),
        @JsonSubTypes.Type(value = Leave.class, name = "Leave"),
        @JsonSubTypes.Type(value = Ping.class, name = "Ping"),
        @JsonSubTypes.Type(value = Pong.class, name = "Pong"),
        @JsonSubTypes.Type(value = RequestClientsStocktaking.class, name = "RequestClientsStocktaking"),
        @JsonSubTypes.Type(value = Response.class, name = "Response"),
        @JsonSubTypes.Type(value = Status.class, name = "Status"),
        @JsonSubTypes.Type(value = ResponsiblePeer.class, name = "ResponsiblePeer"),
        @JsonSubTypes.Type(value = ClientJoined.class, name = "ClientJoined"),
        @JsonSubTypes.Type(value = ClientLeave.class, name = "ClientLeave"),
        @JsonSubTypes.Type(value = ConnectedClients.class, name = "ConnectedClients"),
        @JsonSubTypes.Type(value = ForwardableP2PMessage.class, name = "ForwardableP2PMessage"),
        @JsonSubTypes.Type(value = HandoffCompleted.class, name = "HandoffCompleted"),
        @JsonSubTypes.Type(value = InitComplete.class, name = "InitComplete"),
        @JsonSubTypes.Type(value = NetworkPeers.class, name = "NetworkPeers"),
        @JsonSubTypes.Type(value = PeerJoined.class, name = "PeerJoined"),
        @JsonSubTypes.Type(value = PeerOffline.class, name = "PeerOffline"),
        @JsonSubTypes.Type(value = RequestClientConnected.class, name = "RequestClientConnected"),
        @JsonSubTypes.Type(value = RequestConnectedClients.class, name = "RequestConnectedClients"),
        @JsonSubTypes.Type(value = RequestNetworkPeers.class, name = "RequestNetworkPeers")
})
public interface Message {
    String getMessageID();
}
