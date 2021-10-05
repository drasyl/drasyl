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
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class GroupJoinedEventTest {
    @Mock
    private Group group;
    @Mock
    private IdentityPublicKey member;
    @Mock
    private Runnable runnable;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectGroup() {
            final GroupJoinedEvent event = GroupJoinedEvent.of(group, Set.of(member), runnable);

            assertEquals(group, event.getGroup());
        }

        @Test
        void shouldReturnCorrectMember() {
            final GroupJoinedEvent event = GroupJoinedEvent.of(group, Set.of(member), runnable);

            assertEquals(Set.of(member), event.getMembers());
        }

        @Test
        void shouldReturnCorrectRunnable() {
            final GroupJoinedEvent event = GroupJoinedEvent.of(group, Set.of(member), runnable);

            assertEquals(runnable, event.getLeaveRun());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinedEvent event1 = GroupJoinedEvent.of(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = GroupJoinedEvent.of(group, Set.of(), runnable);

            assertEquals(event1, event2);
        }

        @Test
        void shouldNotBeEquals(@Mock final Group group2) {
            final GroupJoinedEvent event1 = GroupJoinedEvent.of(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = GroupJoinedEvent.of(group2, Set.of(member), runnable);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinedEvent event1 = GroupJoinedEvent.of(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = GroupJoinedEvent.of(group, Set.of(), runnable);

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        void shouldNotBeEquals(@Mock final Group group2) {
            final GroupJoinedEvent event1 = GroupJoinedEvent.of(group, Set.of(member), runnable);
            final GroupJoinedEvent event2 = GroupJoinedEvent.of(group2, Set.of(member), runnable);

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }
    }
}
