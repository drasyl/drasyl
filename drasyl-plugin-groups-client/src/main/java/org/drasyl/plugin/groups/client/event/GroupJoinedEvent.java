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
package org.drasyl.plugin.groups.client.event;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;

import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * An event that signals that this node has successfully joined a group.
 * <p>
 * This is an immutable object.
 */
public class GroupJoinedEvent implements GroupEvent {
    private final Group group;
    private final Set<CompressedPublicKey> members;
    private final Runnable leaveRun;

    /**
     * @throws NullPointerException if {@code group}, {@code members} or {@code leaveRun} is {@code
     *                              null}
     * @deprecated Use {@link #of(Group, Set, Runnable)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.5.0", forRemoval = true)
    public GroupJoinedEvent(final Group group,
                            final Set<CompressedPublicKey> members,
                            final Runnable leaveRun) {
        this.group = requireNonNull(group);
        this.members = Set.copyOf(members);
        this.leaveRun = requireNonNull(leaveRun);
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

    /**
     * @throws NullPointerException if {@code group}, {@code members} or {@code leaveRun} is {@code
     *                              null}
     */
    public static GroupJoinedEvent of(final Group group,
                                      final Set<CompressedPublicKey> members,
                                      final Runnable leaveRun) {
        return new GroupJoinedEvent(group, members, leaveRun);
    }
}
