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
package org.drasyl.node.plugin.groups.manager.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * Class is used to model the state of a group member.
 * <p>
 * <b>This class should only plugin internally used.</b>
 * </p>
 */
public final class Membership {
    private final Member member;
    private final Group group;
    private final long staleAt;

    private Membership(final Member member,
                       final Group group,
                       final long staleAt) {
        this.member = Objects.requireNonNull(member);
        this.group = Objects.requireNonNull(group);
        this.staleAt = staleAt;
    }

    public Member getMember() {
        return member;
    }

    @JsonIgnore
    public Group getGroup() {
        return group;
    }

    public long getStaleAt() {
        return staleAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(member, group, staleAt);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Membership that = (Membership) o;
        return staleAt == that.staleAt &&
                Objects.equals(member, that.member) &&
                Objects.equals(group, that.group);
    }

    @Override
    public String toString() {
        return "Membership{" +
                "member=" + member +
                ", group=" + group +
                ", staleAt=" + staleAt +
                '}';
    }

    public static Membership of(final Member member,
                                final Group group,
                                final long staleAt) {
        return new Membership(member, group, staleAt);
    }
}
