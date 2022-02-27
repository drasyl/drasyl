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
package org.drasyl.node.plugin.groups.client.event;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.plugin.groups.client.Group;

import java.util.Objects;
import java.util.Set;

/**
 * An event that signals that this node has successfully joined a group.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class GroupJoinedEvent implements GroupEvent {
    /**
     * @throws NullPointerException if {@code group}, {@code members} or {@code leaveRun} is {@code
     *                              null}
     */
    public static GroupJoinedEvent of(final Group group,
                                      final Set<IdentityPublicKey> members,
                                      final Runnable leaveRun) {
        return new AutoValue_GroupJoinedEvent(group, members, leaveRun);
    }

    public abstract Set<IdentityPublicKey> getMembers();

    /**
     * If this runnable is invoked the group will be left.
     *
     * @return runnable to leave the group
     */
    public abstract Runnable getLeaveRun();

    @Override
    public int hashCode() {
        return Objects.hash(getGroup());
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
        return Objects.equals(getGroup(), that.getGroup());
    }
}
