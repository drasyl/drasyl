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

package org.drasyl;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class DrasylExceptionTest {
    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualExceptionsWithCause() {
            final Exception causeA = new Exception();
            final Exception causeB = new Exception();
            final DrasylException exceptionA = new DrasylException(causeA);
            final DrasylException exceptionB = new DrasylException(causeA);
            final DrasylException exceptionC = new DrasylException(causeB);

            assertEquals(exceptionA, exceptionA);
            assertEquals(exceptionA, exceptionB);
            assertEquals(exceptionB, exceptionA);
            assertNotEquals(null, exceptionA);
            assertNotEquals(exceptionA, exceptionC);
            assertNotEquals(exceptionC, exceptionA);
        }

        @Test
        void shouldRecognizeEqualExceptionsWithMessage() {
            final DrasylException exceptionA = new DrasylException("foo");
            final DrasylException exceptionB = new DrasylException("foo");
            final DrasylException exceptionC = new DrasylException("bar");

            assertEquals(exceptionA, exceptionA);
            assertEquals(exceptionA, exceptionB);
            assertEquals(exceptionB, exceptionA);
            assertNotEquals(null, exceptionA);
            assertNotEquals(exceptionA, exceptionC);
            assertNotEquals(exceptionC, exceptionA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualExceptionsWithCause() {
            final Exception causeA = new Exception();
            final Exception causeB = new Exception();
            final DrasylException exceptionA = new DrasylException(causeA);
            final DrasylException exceptionB = new DrasylException(causeA);
            final DrasylException exceptionC = new DrasylException(causeB);

            assertEquals(exceptionA.hashCode(), exceptionB.hashCode());
            assertNotEquals(exceptionA.hashCode(), exceptionC.hashCode());
            assertNotEquals(exceptionB.hashCode(), exceptionC.hashCode());
        }

        @Test
        void shouldRecognizeEqualExceptionsWithMessage() {
            final DrasylException exceptionA = new DrasylException("foo");
            final DrasylException exceptionB = new DrasylException("foo");
            final DrasylException exceptionC = new DrasylException("bar");

            assertEquals(exceptionA.hashCode(), exceptionB.hashCode());
            assertNotEquals(exceptionA.hashCode(), exceptionC.hashCode());
            assertNotEquals(exceptionB.hashCode(), exceptionC.hashCode());
        }
    }
}