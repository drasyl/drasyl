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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class GroupLeftEventTest {
    @Mock
    Runnable runnable;
    @Mock
    private Group group;

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectGroup() {
            final GroupLeftEvent event = new GroupLeftEvent(group, runnable);

            assertEquals(group, event.getGroup());
        }

        @Test
        void shouldReturnCorrectRunnable() {
            final GroupLeftEvent event = new GroupLeftEvent(group, runnable);

            assertEquals(runnable, event.getReJoin());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupLeftEvent event1 = new GroupLeftEvent(group, runnable);
            final GroupLeftEvent event2 = new GroupLeftEvent(group, runnable);

            assertEquals(event1, event2);
        }

        @Test
        void shouldNotBeEquals(@Mock final Group group2) {
            final GroupLeftEvent event1 = new GroupLeftEvent(group, runnable);
            final GroupLeftEvent event2 = new GroupLeftEvent(group2, runnable);

            assertNotEquals(event1, event2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupLeftEvent event1 = new GroupLeftEvent(group, runnable);
            final GroupLeftEvent event2 = new GroupLeftEvent(group, runnable);

            assertEquals(event1.hashCode(), event2.hashCode());
        }

        @Test
        void shouldNotBeEquals(@Mock final Group group2) {
            final GroupLeftEvent event1 = new GroupLeftEvent(group, runnable);
            final GroupLeftEvent event2 = new GroupLeftEvent(group2, runnable);

            assertNotEquals(event1.hashCode(), event2.hashCode());
        }
    }
}
