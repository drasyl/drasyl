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