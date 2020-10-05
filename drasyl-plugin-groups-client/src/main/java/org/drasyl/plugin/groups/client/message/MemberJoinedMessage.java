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
package org.drasyl.plugin.groups.client.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;

/**
 * This message is sent by the groups server to the client when a new member has joined a group.
 * <p>
 * This is an immutable object.
 */
public class MemberJoinedMessage extends MemberActionMessage implements GroupsServerMessage {
    @JsonCreator
    public MemberJoinedMessage(@JsonProperty("member") final CompressedPublicKey member,
                               @JsonProperty("group") final Group group) {
        super(member, group);
    }

    @Override
    public String toString() {
        return "MemberJoinedMessage{" +
                "member=" + member +
                ", group='" + group + '\'' +
                '}';
    }
}
