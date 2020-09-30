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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugin.groups.client.event;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GroupJoinedEventTest {
    @Mock
    private Group group;
    @Mock
    private CompressedPublicKey member;
    @Mock
    private Runnable runnable;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectGroup() {
            final GroupJoinedEvent event = new GroupJoinedEvent(group, Set.of(member), runnable);

            assertEquals(group, event.getGroup());
        }

        @Test
        void shouldReturnCorrectMember() {
            final GroupJoinedEvent event = new GroupJoinedEvent(group, Set.of(member), runnable);

            assertEquals(Set.of(member), event.getMembers());
        }

        @Test
        void shouldReturnCorrectRunnable() {
            final GroupJoinedEvent event = new GroupJoinedEvent(group, Set.of(member), runnable);

            assertEquals(runnable, event.getLeaveRun());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinedEvent event1 = new GroupJoinedEvent(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = new GroupJoinedEvent(group, Set.of(), runnable);

            assertEquals(event1, event2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinedEvent event1 = new GroupJoinedEvent(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = new GroupJoinedEvent(mock(Group.class), Set.of(member), runnable);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinedEvent event1 = new GroupJoinedEvent(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = new GroupJoinedEvent(group, Set.of(), runnable);

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinedEvent event1 = new GroupJoinedEvent(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = new GroupJoinedEvent(mock(Group.class), Set.of(member), runnable);

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }
    }
}