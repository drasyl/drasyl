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
import org.drasyl.node.plugin.groups.client.Group;
import org.drasyl.node.plugin.groups.client.message.GroupJoinFailedMessage;

import java.util.Objects;

/**
 * An event that signals, that a joining a specific group has failed.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class GroupJoinFailedEvent implements GroupEvent {
    public abstract GroupJoinFailedMessage.Error getReason();

    /**
     * If this runnable is invoked the plugin tries to re-join the group.
     *
     * @return runnable to re-join group
     */
    public abstract Runnable getReJoin();

    /**
     * @throws NullPointerException if {@code group}, {@code reason} or {@code reJoin} is {@code
     *                              null}
     */
    public static GroupJoinFailedEvent of(final Group group,
                                          final GroupJoinFailedMessage.Error reason,
                                          final Runnable reJoin) {
        return new AutoValue_GroupJoinFailedEvent(group, reason, reJoin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGroup(), getReason());
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
        return Objects.equals(getGroup(), that.getGroup()) & Objects.equals(getReason(), that.getReason());
    }
}
