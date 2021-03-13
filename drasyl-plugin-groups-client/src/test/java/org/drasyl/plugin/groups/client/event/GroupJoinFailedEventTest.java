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

import org.drasyl.plugin.groups.client.Group;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error;
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
            final GroupJoinFailedEvent event = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);

            assertEquals(Error.ERROR_UNKNOWN, event.getReason());
        }

        @Test
        void shouldReturnCorrectGroup() {
            final GroupJoinFailedEvent event = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);

            assertEquals(group, event.getGroup());
        }

        @Test
        void shouldReturnCorrectRunnable() {
            final GroupJoinFailedEvent event = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);

            assertEquals(runnable, event.getReJoin());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);

            assertEquals(event1, event2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, Error.ERROR_GROUP_NOT_FOUND, runnable);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinFailedEvent event1 = GroupJoinFailedEvent.of(group, Error.ERROR_UNKNOWN, runnable);
            final GroupJoinFailedEvent event2 = GroupJoinFailedEvent.of(group, Error.ERROR_GROUP_NOT_FOUND, runnable);

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }
    }
}
