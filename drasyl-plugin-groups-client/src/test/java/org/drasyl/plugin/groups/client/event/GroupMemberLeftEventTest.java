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
package org.drasyl.plugin.groups.client.event;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class GroupMemberLeftEventTest {
    @Mock
    private CompressedPublicKey member;
    @Mock
    private Group group;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectMember() {
            final GroupMemberLeftEvent event = new GroupMemberLeftEvent(member, group);

            assertEquals(member, event.getMember());
        }

        @Test
        void shouldReturnCorrectGroup() {
            final GroupMemberLeftEvent event = new GroupMemberLeftEvent(member, group);

            assertEquals(member, event.getMember());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupMemberLeftEvent event1 = new GroupMemberLeftEvent(member, group);
            final GroupMemberLeftEvent event2 = new GroupMemberLeftEvent(member, group);

            assertEquals(event1, event2);
        }

        @Test
        void shouldNotBeEquals(@Mock final CompressedPublicKey member2) {
            final GroupMemberLeftEvent event1 = new GroupMemberLeftEvent(member, group);
            final GroupMemberLeftEvent event2 = new GroupMemberLeftEvent(member2, group);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupMemberLeftEvent event1 = new GroupMemberLeftEvent(member, group);
            final GroupMemberLeftEvent event2 = new GroupMemberLeftEvent(member, group);

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        void shouldNotBeEquals(@Mock final CompressedPublicKey member2) {
            final GroupMemberLeftEvent event1 = new GroupMemberLeftEvent(member, group);
            final GroupMemberLeftEvent event2 = new GroupMemberLeftEvent(member2, group);

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }
    }
}
