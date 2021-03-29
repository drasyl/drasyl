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
