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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.client.event;

import org.drasyl.plugin.groups.client.Group;

import java.util.Objects;

/**
 * An event that signals that this node has left a group. (Maybe got also kicked by the group
 * manager)
 * <p>
 * This is an immutable object.
 */
public class GroupLeftEvent implements GroupEvent {
    private final Group group;
    private final Runnable reJoin;

    public GroupLeftEvent(final Group group,
                          final Runnable reJoin) {
        this.group = Objects.requireNonNull(group);
        this.reJoin = Objects.requireNonNull(reJoin);
    }

    @Override
    public Group getGroup() {
        return group;
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
        final GroupLeftEvent that = (GroupLeftEvent) o;
        return Objects.equals(group, that.group);
    }

    @Override
    public String toString() {
        return "GroupLeftEvent{" +
                "group=" + group +
                '}';
    }

    /**
     * If this runnable is invoked the plugin tries to re-join the {@link #group}.
     *
     * @return runnable to re-join group
     */
    public Runnable getReJoin() {
        return reJoin;
    }
}
