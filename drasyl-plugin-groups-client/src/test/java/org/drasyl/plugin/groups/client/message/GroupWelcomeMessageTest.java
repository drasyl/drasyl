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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.plugin.groups.client.message;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupWelcomeMessageTest {
    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final Set<CompressedPublicKey> set = Set.of();
            final Group group = Group.of("my-squad");
            assertThrows(NullPointerException.class, () -> new GroupWelcomeMessage(group, null));
            assertThrows(NullPointerException.class, () -> new GroupWelcomeMessage(null, set));
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final GroupWelcomeMessage msg = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }

        @Test
        void shouldReturnCorrectMemberSet() {
            final GroupWelcomeMessage msg = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());

            assertEquals(Set.of(), msg.getMembers());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupWelcomeMessage msg1 = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());
            final GroupWelcomeMessage msg2 = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupWelcomeMessage msg1 = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());
            final GroupWelcomeMessage msg2 = new GroupWelcomeMessage(Group.of("your-squad"), Set.of());

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupWelcomeMessage msg1 = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());
            final GroupWelcomeMessage msg2 = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupWelcomeMessage msg1 = new GroupWelcomeMessage(Group.of("my-squad"), Set.of());
            final GroupWelcomeMessage msg2 = new GroupWelcomeMessage(Group.of("your-squad"), Set.of());

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}