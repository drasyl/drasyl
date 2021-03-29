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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeParameterMatcherTest {
    @Nested
    class Find {
        @SuppressWarnings("rawtypes")
        @Test
        void testUnsolvedParameter() {
            final TypeQ object = new TypeQ();
            assertThrows(IllegalStateException.class, () -> TypeParameterMatcher.find(object, TypeX.class, "B"));
        }
    }

    @Nested
    class Match {
        @SuppressWarnings("rawtypes")
        @Test
        void shouldMatchConcreteClass() {
            final TypeParameterMatcher m = TypeParameterMatcher.find(new TypeQ(), TypeX.class, "A");
            assertFalse(m.match(new Object()));
            assertFalse(m.match(new A()));
            assertFalse(m.match(new AA()));
            assertTrue(m.match(new AAA()));
            assertFalse(m.match(new B()));
            assertFalse(m.match(new BB()));
            assertFalse(m.match(new BBB()));
            assertFalse(m.match(new C()));
            assertFalse(m.match(new CC()));
        }

        @SuppressWarnings("rawtypes")
        @Test
        void testAbstractClass() {
            final TypeParameterMatcher m = TypeParameterMatcher.find(new TypeQ(), TypeX.class, "C");
            assertFalse(m.match(new Object()));
            assertFalse(m.match(new A()));
            assertFalse(m.match(new AA()));
            assertFalse(m.match(new AAA()));
            assertFalse(m.match(new B()));
            assertFalse(m.match(new BB()));
            assertFalse(m.match(new BBB()));
            assertFalse(m.match(new C()));
            assertTrue(m.match(new CC()));
        }

        @Test
        void testInaccessibleClass() {
            final TypeParameterMatcher m = TypeParameterMatcher.find(new U<T>() {
            }, U.class, "E");
            assertFalse(m.match(new Object()));
            assertTrue(m.match(new T()));
        }

        @Test
        void testArrayAsTypeParam() {
            final TypeParameterMatcher m = TypeParameterMatcher.find(new U<byte[]>() {
            }, U.class, "E");
            assertFalse(m.match(new Object()));
            assertTrue(m.match(new byte[1]));
        }

        @SuppressWarnings("rawtypes")
        @Test
        void testRawType() {
            final TypeParameterMatcher m = TypeParameterMatcher.find(new U() {
            }, U.class, "E");
            assertTrue(m.match(new Object()));
        }

        @Test
        void testInnerClass() {
            final TypeParameterMatcher m = TypeParameterMatcher.find(new V<String>().u, U.class, "E");
            assertTrue(m.match(new Object()));
        }

        @Test
        void testErasure() {
            final X<String, Date> object = new X<>();
            assertThrows(IllegalStateException.class, () -> TypeParameterMatcher.find(object, W.class, "E"));
        }
    }

    @SuppressWarnings("unused")
    public static class TypeX<A, B, C> {
        A a;
        B b;
        C c;
    }

    public static class TypeY<D extends C, E extends A, F extends B> extends TypeX<E, F, D> {
    }

    public abstract static class TypeZ<G extends AA, H extends BB> extends TypeY<CC, G, H> {
    }

    public static class TypeQ<I extends BBB> extends TypeZ<AAA, I> {
    }

    @SuppressWarnings("ClassMayBeInterface")
    public static class A {
    }

    public static class AA extends A {
    }

    public static class AAA extends AA {
    }

    @SuppressWarnings("ClassMayBeInterface")
    public static class B {
    }

    public static class BB extends B {
    }

    public static class BBB extends BB {
    }

    @SuppressWarnings("ClassMayBeInterface")
    public static class C {
    }

    public static class CC extends C {
    }

    @SuppressWarnings("ClassMayBeInterface")
    private static class T {
    }

    @SuppressWarnings("unused")
    private static class U<E> {
        E a;
    }

    private static class V<E> {
        U<E> u = new U<>() {
        };
    }

    @SuppressWarnings("unused")
    private abstract static class W<E> {
        E e;
    }

    @SuppressWarnings("unused")
    private static class X<T, E> extends W<E> {
        T t;
    }
}
