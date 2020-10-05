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
package org.drasyl.plugin.groups.client.event;

import org.drasyl.plugin.groups.client.Group;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error;

import java.util.Objects;

/**
 * An event that signals, that a joining a specific group has failed.
 * <p>
 * This is an immutable object.
 */
public class GroupJoinFailedEvent implements GroupEvent {
    private final Group group;
    private final Error reason;
    private final Runnable reJoin;

    public GroupJoinFailedEvent(final Group group, final Error reason, final Runnable reJoin) {
        this.group = group;
        this.reason = reason;
        this.reJoin = reJoin;
    }

    public Error getReason() {
        return reason;
    }

    @Override
    public Group getGroup() {
        return group;
    }

    /**
     * If this runnable is invoked the plugin tries to re-join the {@link #group}.
     *
     * @return runnable to re-join group
     */
    public Runnable getReJoin() {
        return reJoin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, reason);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupJoinFailedEvent that = (GroupJoinFailedEvent) o;
        return Objects.equals(group, that.group) &&
                Objects.equals(reason, that.reason);
    }

    @Override
    public String toString() {
        return "GroupJoinFailedEvent{" +
                "group=" + group +
                ", reason='" + reason + '\'' +
                '}';
    }
}
