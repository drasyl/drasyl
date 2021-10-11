/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.node.plugin.groups.client.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.plugin.groups.client.Group;

import java.util.Objects;
import java.util.Set;

/**
 * This message is sent by the groups server to the client when the join to a group was successful.
 * <p>
 * This is an immutable object.
 */
public class GroupWelcomeMessage extends GroupActionMessage implements GroupsServerMessage {
    private final Set<IdentityPublicKey> members;

    @JsonCreator
    public GroupWelcomeMessage(@JsonProperty("group") final Group group,
                               @JsonProperty("members") final Set<IdentityPublicKey> members) {
        super(group);
        this.members = Set.copyOf(members);
    }

    public Set<IdentityPublicKey> getMembers() {
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
