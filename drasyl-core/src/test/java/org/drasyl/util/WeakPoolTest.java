package org.drasyl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class WeakPoolTest {
    @Test
    void shouldReturnEqualObjectIfPoolContainsEqualObject() {
        final MyObject a = new MyObject("Hello World");
        final MyObject b = new MyObject("Hello World");

        final WeakPool<Object> pool = new WeakPool<>();
        pool.put(a);

        assertSame(a, pool.get(b));
    }

    @Test
    void shouldReturnNullIfPoolDoesNotContainsEqualObject() {
        final MyObject a = new MyObject("Hello World");
        final MyObject b = new MyObject("Bye Moon");

        final WeakPool<MyObject> pool = new WeakPool<>();
        pool.put(a);

        assertNull(pool.get(b));
    }

    @Test
    void shouldRemoveUnreferencedObjectsOnGarbageCollection() {
        MyObject a = new MyObject("Hello World");
        final MyObject b = new MyObject("Hello World");

        final WeakPool<MyObject> pool = new WeakPool<>();
        pool.put(a);

        a = null;
        System.gc();

        assertNull(a);
        assertNull(pool.get(b));
    }

    static class MyObject {
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