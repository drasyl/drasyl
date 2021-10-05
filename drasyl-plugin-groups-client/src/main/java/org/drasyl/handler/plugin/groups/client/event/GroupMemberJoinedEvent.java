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
import org.drasyl.identity.IdentityPublicKey;

/**
 * An event that signals that a new member joined a group.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings("java:S2974")
public class GroupMemberJoinedEvent extends GroupMemberActionEvent {
    /**
     * @throws NullPointerException if {@code member} or {@code group} is {@code null}
     */
    private GroupMemberJoinedEvent(final IdentityPublicKey member,
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

    /**
     * @throws NullPointerException if {@code member} or {@code group} is {@code null}
     */
    public static GroupMemberJoinedEvent of(final IdentityPublicKey member,
                                            final Group group) {
        return new GroupMemberJoinedEvent(member, group);
    }
}
