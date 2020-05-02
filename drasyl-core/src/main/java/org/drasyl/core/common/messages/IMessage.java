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
package org.drasyl.core.common.messages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.drasyl.crypto.Signable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Welcome.class, name = "Welcome"),
        @JsonSubTypes.Type(value = ClientsStocktaking.class, name = "ClientsStocktaking"),
        @JsonSubTypes.Type(value = NodeServerException.class, name = "NodeServerException"),
        @JsonSubTypes.Type(value = Message.class, name = "Message"),
        @JsonSubTypes.Type(value = Join.class, name = "Join"),
        @JsonSubTypes.Type(value = Leave.class, name = "Leave"),
        @JsonSubTypes.Type(value = Ping.class, name = "Ping"),
        @JsonSubTypes.Type(value = Pong.class, name = "Pong"),
        @JsonSubTypes.Type(value = RequestClientsStocktaking.class, name = "RequestClientsStocktaking"),
        @JsonSubTypes.Type(value = Response.class, name = "Response"),
        @JsonSubTypes.Type(value = Status.class, name = "Status")
})
public interface IMessage extends Signable {
    String getMessageID();
}
