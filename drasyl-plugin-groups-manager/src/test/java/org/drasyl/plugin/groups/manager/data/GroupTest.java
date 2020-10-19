/*
 * Copyright (c) 2020.
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
package org.drasyl.plugin.groups.manager.data;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupTest {
    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectName() {
            final Group group = Group.of("test", "secret", (short) 0, ofSeconds(60));

            assertEquals("test", group.getName());
        }

        @Test
        void shouldReturnCorrectSecret() {
            final Group group = Group.of("test", "secret", (short) 0, ofSeconds(60));

            assertEquals("secret", group.getCredentials());
        }

        @Test
        void shouldReturnCorrectMinDifficulty() {
            final Group group = Group.of("test", "secret", (short) 0, ofSeconds(60));

            assertEquals(0, group.getMinDifficulty());
        }

        @Test
        void shouldReturnCorrectTimeout() {
            final Group group = Group.of("test", "secret", (short) 0, ofSeconds(60));

            assertEquals(ofSeconds(60), group.getTimeout());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final Group group1 = Group.of("test", "secret", (short) 0, ofSeconds(60));
            final Group group2 = Group.of("test", "secret", (short) 0, ofSeconds(60));

            assertEquals(group1, group2);
        }

        @Test
        void shouldNotBeEquals() {
            final Group group1 = Group.of("test1", "secret", (short) 0, ofSeconds(60));
            final Group group2 = Group.of("test2", "secret", (short) 0, ofSeconds(60));

            assertNotEquals(group1, group2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final Group group1 = Group.of("test", "secret", (short) 0, ofSeconds(60));
            final Group group2 = Group.of("test", "secret", (short) 0, ofSeconds(60));

            assertEquals(group1.hashCode(), group2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final Group group1 = Group.of("test1", "secret", (short) 0, ofSeconds(60));
            final Group group2 = Group.of("test2", "secret", (short) 0, ofSeconds(60));

            assertNotEquals(group1.hashCode(), group2.hashCode());
        }
    }

    @Nested
    class Of {
        @Test
        void shouldThrowExceptionOnInvalidMinDifficulty() {
            final Duration timeout = ofSeconds(60);
            assertThrows(IllegalArgumentException.class, () -> Group.of("vip-gang", "secret", (short) -1, timeout));
        }

        @Test
        void shouldThrowExceptionOnInvalidTimeout() {
            final Duration timeout = ofSeconds(1);
            assertThrows(IllegalArgumentException.class, () -> Group.of("vip-gang", "secret", (short) 0, timeout));
        }
    }
}