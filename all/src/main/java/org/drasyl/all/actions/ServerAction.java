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

package org.drasyl.all.actions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.drasyl.all.Drasyl;
import org.drasyl.all.actions.messages.*;
import org.drasyl.all.messages.Message;
import org.drasyl.all.session.Session;

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
    void onMessage(Session client, Drasyl relay);

    /**
     * Handles incoming responses and passes them to the correct sub-function.
     *
     * @param responseMsgID the id of the corresponding message
     */
    void onResponse(String responseMsgID, Session client, Drasyl relay);
}
