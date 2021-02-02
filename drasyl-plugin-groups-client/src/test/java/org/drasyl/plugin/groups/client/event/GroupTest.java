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
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class GroupTest {
    @Nested
    class Getters {
        @Test
        void shouldReturnName() {
            final Group group = Group.of("name");

            assertEquals("name", group.getName());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final Group group1 = Group.of("name");
            final Group group2 = Group.of("name");

            assertEquals(group1, group2);
        }

        @Test
        void shouldNotBeEquals() {
            final Group group1 = Group.of("name1");
            final Group group2 = Group.of("name2");

            assertNotEquals(group1, group2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final Group group1 = Group.of("name");
            final Group group2 = Group.of("name");

            assertEquals(group1.hashCode(), group2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final Group group1 = Group.of("name1");
            final Group group2 = Group.of("name2");

            assertNotEquals(group1.hashCode(), group2.hashCode());
        }
    }
}
