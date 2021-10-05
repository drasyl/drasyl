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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * An event that signals that this node has left a group. (Maybe got also kicked by the group
 * manager)
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings("java:S2974")
public class GroupLeftEvent implements GroupEvent {
    private final Group group;
    private final Runnable reJoin;

    /**
     * @throws NullPointerException if {@code group} or {@code reJoin} is {@code null}
     */
    private GroupLeftEvent(final Group group,
                           final Runnable reJoin) {
        this.group = requireNonNull(group);
        this.reJoin = requireNonNull(reJoin);
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

    /**
     * @throws NullPointerException if {@code group} or{@code reJoin} is {@code null}
     */
    public static GroupLeftEvent of(final Group group,
                                    final Runnable reJoin) {
        return new GroupLeftEvent(group, reJoin);
    }
}
