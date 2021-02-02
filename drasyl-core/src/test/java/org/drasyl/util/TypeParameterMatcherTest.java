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
