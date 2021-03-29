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

import org.drasyl.plugin.groups.client.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupLeaveMessageTest {
    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new GroupLeaveMessage(null));
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final GroupLeaveMessage msg = new GroupLeaveMessage(Group.of("my-squad"));

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupLeaveMessage msg1 = new GroupLeaveMessage(Group.of("my-squad"));
            final GroupLeaveMessage msg2 = new GroupLeaveMessage(Group.of("my-squad"));

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupLeaveMessage msg1 = new GroupLeaveMessage(Group.of("my-squad"));
            final GroupLeaveMessage msg2 = new GroupLeaveMessage(Group.of("your-squad"));

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupLeaveMessage msg1 = new GroupLeaveMessage(Group.of("my-squad"));
            final GroupLeaveMessage msg2 = new GroupLeaveMessage(Group.of("my-squad"));

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupLeaveMessage msg1 = new GroupLeaveMessage(Group.of("my-squad"));
            final GroupLeaveMessage msg2 = new GroupLeaveMessage(Group.of("your-squad"));

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}
