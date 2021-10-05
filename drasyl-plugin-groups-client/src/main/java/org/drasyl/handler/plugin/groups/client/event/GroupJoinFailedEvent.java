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
package org.drasyl.handler.plugin.groups.client.event;

import org.drasyl.handler.plugin.groups.client.Group;
import org.drasyl.handler.plugin.groups.client.message.GroupJoinFailedMessage;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * An event that signals, that a joining a specific group has failed.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings("java:S2974")
public class GroupJoinFailedEvent implements GroupEvent {
    private final Group group;
    private final GroupJoinFailedMessage.Error reason;
    private final Runnable reJoin;

    /**
     * @throws NullPointerException if {@code group}, {@code reason} or {@code reJoin} is {@code
     *                              null}
     */
    private GroupJoinFailedEvent(final Group group,
                                 final GroupJoinFailedMessage.Error reason,
                                 final Runnable reJoin) {
        this.group = requireNonNull(group);
        this.reason = requireNonNull(reason);
        this.reJoin = requireNonNull(reJoin);
    }

    public GroupJoinFailedMessage.Error getReason() {
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

    /**
     * @throws NullPointerException if {@code group}, {@code reason} or {@code reJoin} is {@code
     *                              null}
     */
    public static GroupJoinFailedEvent of(final Group group,
                                          final GroupJoinFailedMessage.Error reason,
                                          final Runnable reJoin) {
        return new GroupJoinFailedEvent(group, reason, reJoin);
    }
}
