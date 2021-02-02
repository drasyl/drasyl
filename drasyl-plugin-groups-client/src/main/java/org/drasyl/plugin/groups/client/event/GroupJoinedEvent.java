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

import java.util.Objects;
import java.util.Set;

/**
 * An event that signals that this node has successfully joined a group.
 * <p>
 * This is an immutable object.
 */
public class GroupJoinedEvent implements GroupEvent {
    private final Group group;
    private final Set<CompressedPublicKey> members;
    private final Runnable leaveRun;

    public GroupJoinedEvent(final Group group,
                            final Set<CompressedPublicKey> members,
                            final Runnable leaveRun) {
        this.group = Objects.requireNonNull(group);
        this.members = Objects.requireNonNull(members);
        this.leaveRun = Objects.requireNonNull(leaveRun);
    }

    @Override
    public Group getGroup() {
        return group;
    }

    public Set<CompressedPublicKey> getMembers() {
        return members;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupJoinedEvent that = (GroupJoinedEvent) o;
        return Objects.equals(group, that.group);
    }

    @Override
    public String toString() {
        return "GroupJoinedEvent{" +
                "group=" + group +
                ", members=" + members +
                '}';
    }

    /**
     * If this runnable is invoked the {@link #group} will be left.
     *
     * @return runnable to left the {@link #group}
     */
    public Runnable getLeaveRun() {
        return leaveRun;
    }
}
