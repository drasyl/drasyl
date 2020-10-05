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

import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * This message is sent by the groups server to the client when the join to a group was successful.
 * <p>
 * This is an immutable object.
 */
public class GroupWelcomeMessage extends GroupActionMessage implements GroupsServerMessage {
    private final Set<CompressedPublicKey> members;

    @JsonCreator
    public GroupWelcomeMessage(@JsonProperty("group") final Group group,
                               @JsonProperty("members") final Set<CompressedPublicKey> members) {
        super(group);
        this.members = requireNonNull(members);
    }

    public Set<CompressedPublicKey> getMembers() {
        return members;
    }

    @Override
    public String toString() {
        return "GroupWelcomeMessage{" +
                "group='" + group + '\'' +
                ", members=" + members +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final GroupWelcomeMessage that = (GroupWelcomeMessage) o;
        return Objects.equals(members, that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), members);
    }
}
