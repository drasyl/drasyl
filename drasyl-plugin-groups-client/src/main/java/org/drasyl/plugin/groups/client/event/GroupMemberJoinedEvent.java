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
 */
public class GroupMemberJoinedEvent extends GroupMemberActionEvent {
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
}
