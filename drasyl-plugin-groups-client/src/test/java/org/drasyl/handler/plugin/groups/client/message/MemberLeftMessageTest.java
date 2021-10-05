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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MemberLeftMessageTest {
    @Mock
    private IdentityPublicKey publicKey;

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final Group group = Group.of("my-squad");
            assertThrows(NullPointerException.class, () -> new MemberLeftMessage(publicKey, null));
            assertThrows(NullPointerException.class, () -> new MemberLeftMessage(null, group));
        }

        @Test
        void shouldReturnCorrectMember() {
            final MemberLeftMessage msg = new MemberLeftMessage(publicKey, Group.of("my-squad"));

            assertEquals(publicKey, msg.getMember());

            // ignore toString()
            msg.toString();
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final MemberLeftMessage msg = new MemberLeftMessage(publicKey, Group.of("my-squad"));

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final MemberLeftMessage msg1 = new MemberLeftMessage(publicKey, Group.of("my-squad"));
            final MemberLeftMessage msg2 = new MemberLeftMessage(publicKey, Group.of("my-squad"));

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final MemberLeftMessage msg1 = new MemberLeftMessage(publicKey, Group.of("my-squad"));
            final MemberLeftMessage msg2 = new MemberLeftMessage(publicKey, Group.of("your-squad"));

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final MemberLeftMessage msg1 = new MemberLeftMessage(publicKey, Group.of("my-squad"));
            final MemberLeftMessage msg2 = new MemberLeftMessage(publicKey, Group.of("my-squad"));

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final MemberLeftMessage msg1 = new MemberLeftMessage(publicKey, Group.of("my-squad"));
            final MemberLeftMessage msg2 = new MemberLeftMessage(publicKey, Group.of("your-squad"));

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}
