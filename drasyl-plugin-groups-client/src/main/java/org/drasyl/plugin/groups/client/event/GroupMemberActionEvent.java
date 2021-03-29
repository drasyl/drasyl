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

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("java:S118")
abstract class GroupMemberActionEvent implements GroupEvent {
    protected final CompressedPublicKey member;
    protected final Group group;

    protected GroupMemberActionEvent(
            final CompressedPublicKey member, final Group group) {
        this.member = requireNonNull(member);
        this.group = requireNonNull(group);
    }

    public CompressedPublicKey getMember() {
        return member;
    }

    @Override
    public Group getGroup() {
        return group;
    }

    @Override
    public int hashCode() {
        return hash(member, group);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupMemberActionEvent that = (GroupMemberActionEvent) o;
        return Objects.equals(member, that.member) &&
                Objects.equals(group, that.group);
    }
}
