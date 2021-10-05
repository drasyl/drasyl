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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.handler.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupJoinFailedMessageTest {
    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final Group group = Group.of("my-squad");
            assertThrows(NullPointerException.class, () -> new GroupJoinFailedMessage(group, null));
            assertThrows(NullPointerException.class, () -> new GroupJoinFailedMessage(null, ERROR_GROUP_NOT_FOUND));
        }

        @Test
        void shouldReturnCorrectReason() {
            final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(ERROR_GROUP_NOT_FOUND, msg.getReason());
            assertEquals(ERROR_GROUP_NOT_FOUND.getDescription(), msg.getReason().getDescription());
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }

        @Test
        void shouldGenerateCorrectErrorEnum() {
            final GroupJoinFailedMessage.Error error = ERROR_GROUP_NOT_FOUND;

            assertEquals(error, GroupJoinFailedMessage.Error.from(error.getDescription()));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("your-squad"), ERROR_GROUP_NOT_FOUND);

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedMessage msg1 = new GroupJoinFailedMessage(Group.of("my-squad"), ERROR_GROUP_NOT_FOUND);
            final GroupJoinFailedMessage msg2 = new GroupJoinFailedMessage(Group.of("your-squad"), ERROR_GROUP_NOT_FOUND);

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}
