/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin.groups.manager.data;

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
