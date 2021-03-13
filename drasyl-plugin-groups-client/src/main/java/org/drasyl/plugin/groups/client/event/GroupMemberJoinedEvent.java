/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin.groups.client.event;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;

/**
 * An event that signals that a new member joined a group.
 * <p>
 * This is an immutable object.
 */
public class GroupMemberJoinedEvent extends GroupMemberActionEvent {
    /**
     * @throws NullPointerException if {@code member} or {@code group} is {@code null}
     * @deprecated Use {@link #of(CompressedPublicKey, Group)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.5.0", forRemoval = true)
    public GroupMemberJoinedEvent(final CompressedPublicKey member,
                                  final Group group) {
        super(member, group);
    }

    @Override
    public String toString() {
        return "GroupMemberJoinedEvent{" +
                "member=" + member +
                ", group=" + group +
                '}';
    }

    /**
     * @throws NullPointerException if {@code member} or {@code group} is {@code null}
     */
    public static GroupMemberJoinedEvent of(final CompressedPublicKey member,
                                            final Group group) {
        return new GroupMemberJoinedEvent(member, group);
    }
}
