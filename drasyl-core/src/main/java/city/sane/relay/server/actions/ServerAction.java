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

package city.sane.relay.server.actions;

import city.sane.relay.common.messages.Message;
import city.sane.relay.server.RelayServer;
import city.sane.relay.server.actions.messages.*;
import city.sane.relay.server.session.Session;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerActionPing.class, name = "Ping"),
        @JsonSubTypes.Type(value = ServerActionPong.class, name = "Pong"),
        @JsonSubTypes.Type(value = ServerActionException.class, name = "RelayException"),
        @JsonSubTypes.Type(value = ServerActionLeave.class, name = "Leave"),
        @JsonSubTypes.Type(value = ServerActionForwardableMessage.class, name = "ForwardableMessage"),
        @JsonSubTypes.Type(value = ServerActionRequestClientsStocktaking.class, name = "RequestClientsStocktaking"),
        @JsonSubTypes.Type(value = ServerActionJoin.class, name = "Join"),
        @JsonSubTypes.Type(value = ServerActionResponse.class, name = "Response"),
        @JsonSubTypes.Type(value = ServerActionStatus.class, name = "Status")
})
public interface ServerAction extends Message {
    /**
     * Handles incoming messages and passes them to the correct sub-function.
     */
    void onMessage(Session client, RelayServer relay);

    /**
     * Handles incoming responses and passes them to the correct sub-function.
     *
     * @param responseMsgID the id of the corresponding message
     */
    void onResponse(String responseMsgID, Session client, RelayServer relay);
}
