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
            Exception causeA = new Exception();
            Exception causeB = new Exception();
            DrasylException exceptionA = new DrasylException(causeA);
            DrasylException exceptionB = new DrasylException(causeA);
            DrasylException exceptionC = new DrasylException(causeB);

            assertEquals(exceptionA, exceptionA);
            assertEquals(exceptionA, exceptionB);
            assertEquals(exceptionB, exceptionA);
            assertNotEquals(null, exceptionA);
            assertNotEquals(exceptionA, exceptionC);
            assertNotEquals(exceptionC, exceptionA);
        }

        @Test
        void shouldRecognizeEqualExceptionsWithMessage() {
            DrasylException exceptionA = new DrasylException("foo");
            DrasylException exceptionB = new DrasylException("foo");
            DrasylException exceptionC = new DrasylException("bar");

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
            Exception causeA = new Exception();
            Exception causeB = new Exception();
            DrasylException exceptionA = new DrasylException(causeA);
            DrasylException exceptionB = new DrasylException(causeA);
            DrasylException exceptionC = new DrasylException(causeB);

            assertEquals(exceptionA.hashCode(), exceptionB.hashCode());
            assertNotEquals(exceptionA.hashCode(), exceptionC.hashCode());
            assertNotEquals(exceptionB.hashCode(), exceptionC.hashCode());
        }

        @Test
        void shouldRecognizeEqualExceptionsWithMessage() {
            DrasylException exceptionA = new DrasylException("foo");
            DrasylException exceptionB = new DrasylException("foo");
            DrasylException exceptionC = new DrasylException("bar");

            assertEquals(exceptionA.hashCode(), exceptionB.hashCode());
            assertNotEquals(exceptionA.hashCode(), exceptionC.hashCode());
            assertNotEquals(exceptionB.hashCode(), exceptionC.hashCode());
        }
    }
}