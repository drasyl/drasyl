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
package org.drasyl.core.server.actions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.actions.messages.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerActionPing.class, name = "Ping"),
        @JsonSubTypes.Type(value = ServerActionPong.class, name = "Pong"),
        @JsonSubTypes.Type(value = ServerActionException.class, name = "NodeServerException"),
        @JsonSubTypes.Type(value = ServerActionLeave.class, name = "Leave"),
        @JsonSubTypes.Type(value = ServerActionMessage.class, name = "Message"),
        @JsonSubTypes.Type(value = ServerActionRequestClientsStocktaking.class, name = "RequestClientsStocktaking"),
        @JsonSubTypes.Type(value = ServerActionJoin.class, name = "Join"),
        @JsonSubTypes.Type(value = ServerActionResponse.class, name = "Response"),
        @JsonSubTypes.Type(value = ServerActionStatus.class, name = "Status"),
        @JsonSubTypes.Type(value = ServerActionReject.class, name = "Reject")
})
public interface ServerAction extends IMessage {
    /**
     * Handles incoming messages and passes them to the correct sub-function.
     */
    void onMessage(ClientConnection session, NodeServer nodeServer);

    /**
     * Handles incoming responses and passes them to the correct sub-function.
     *
     * @param responseMsgID the id of the corresponding message
     */
    void onResponse(String responseMsgID, ClientConnection session, NodeServer nodeServer);
}
