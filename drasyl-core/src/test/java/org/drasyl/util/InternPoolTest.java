package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class InternPoolTest {
    @Nested
    class Intern {
        @Test
        void shouldReturnEqualObjectIfPoolContainsEqualObject() {
            final MyObject a = new MyObject("Hello World");
            final MyObject b = new MyObject("Hello World");

            final InternPool<MyObject> pool = new InternPool<>();
            pool.intern(a);

            assertSame(a, pool.intern(b));
        }

        @Test
        void shouldReturnSameObjectIfPoolDoesNotContainsEqualObject() {
            final MyObject a = new MyObject("Hello World");

            final InternPool<MyObject> pool = new InternPool<>();

            assertSame(a, pool.intern(a));
        }
    }

    class MyObject {
        private final String value;

        public MyObject(final String value) {
            this.value = value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MyObject myObject = (MyObject) o;
            return Objects.equals(value, myObject.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}