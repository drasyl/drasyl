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
package org.drasyl.handler.plugin.groups.client.event;

import org.drasyl.handler.plugin.groups.client.Group;
import org.drasyl.handler.plugin.groups.client.message.GroupJoinFailedMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class GroupJoinFailedEventTest {
    @Mock
    private Group group;
    @Mock
    private Runnable runnable;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectReason() {
            final GroupJoinFailedEvent event = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);

            Assertions.assertEquals(GroupJoinFailedMessage.Error.ERROR_UNKNOWN, event.getReason());
        }

        @Test
        void shouldReturnCorrectGroup() {
            final GroupJoinFailedEvent event = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);

            assertEquals(group, event.getGroup());
        }

        @Test
        void shouldReturnCorrectRunnable() {
            final GroupJoinFailedEvent event = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);

            assertEquals(runnable, event.getReJoin());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);

            assertEquals(event1, event2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND, runnable);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND, runnable);

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }
    }
}
