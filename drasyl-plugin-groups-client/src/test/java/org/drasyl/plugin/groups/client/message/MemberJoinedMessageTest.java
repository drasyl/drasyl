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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MemberJoinedMessageTest {
    @Mock
    private CompressedPublicKey publicKey;

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final Group group = Group.of("my-squad");
            assertThrows(NullPointerException.class, () -> new MemberJoinedMessage(publicKey, null));
            assertThrows(NullPointerException.class, () -> new MemberJoinedMessage(null, group));
        }

        @Test
        void shouldReturnCorrectMember() {
            final MemberJoinedMessage msg = new MemberJoinedMessage(publicKey, Group.of("my-squad"));

            assertEquals(publicKey, msg.getMember());
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final MemberJoinedMessage msg = new MemberJoinedMessage(publicKey, Group.of("my-squad"));

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final MemberJoinedMessage msg1 = new MemberJoinedMessage(publicKey, Group.of("my-squad"));
            final MemberJoinedMessage msg2 = new MemberJoinedMessage(publicKey, Group.of("my-squad"));

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final MemberJoinedMessage msg1 = new MemberJoinedMessage(publicKey, Group.of("my-squad"));
            final MemberJoinedMessage msg2 = new MemberJoinedMessage(publicKey, Group.of("your-squad"));

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final MemberJoinedMessage msg1 = new MemberJoinedMessage(publicKey, Group.of("my-squad"));
            final MemberJoinedMessage msg2 = new MemberJoinedMessage(publicKey, Group.of("my-squad"));

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final MemberJoinedMessage msg1 = new MemberJoinedMessage(publicKey, Group.of("my-squad"));
            final MemberJoinedMessage msg2 = new MemberJoinedMessage(publicKey, Group.of("your-squad"));

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}