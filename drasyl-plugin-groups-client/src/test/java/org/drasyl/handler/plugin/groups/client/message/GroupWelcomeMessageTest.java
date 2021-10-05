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
package org.drasyl.handler.plugin.groups.client.message;

import org.drasyl.handler.plugin.groups.client.Group;
import org.drasyl.identity.IdentityPublicKey;
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
            final Set<IdentityPublicKey> set = Set.of();
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
