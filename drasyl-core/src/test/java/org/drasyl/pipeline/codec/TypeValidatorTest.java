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
package org.drasyl.pipeline.codec;

import com.google.common.primitives.Primitives;
import org.drasyl.DrasylConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TypeValidatorTest {
    @Test
    void shouldAllowPrimitivesAutomatically() {
        final TypeValidator validator = TypeValidator.ofInboundValidator(DrasylConfig.newBuilder().build());

        for (final Class<?> clazz : Primitives.allPrimitiveTypes()) {
            assertTrue(validator.validate(clazz));
        }

        for (final Class<?> clazz : Primitives.allWrapperTypes()) {
            assertTrue(validator.validate(clazz));
        }

        assertTrue(validator.validate(Integer[].class));
    }

    @Test
    void shouldNotAllowPrimitivesAutomatically() {
        final TypeValidator validator = new TypeValidator(List.of(), List.of(), false, false, () -> null);

        for (final Class<?> clazz : Primitives.allPrimitiveTypes()) {
            assertFalse(validator.validate(clazz));
        }

        for (final Class<?> clazz : Primitives.allWrapperTypes()) {
            assertFalse(validator.validate(clazz));
        }

        assertFalse(validator.validate(Integer[].class));
    }

    @Test
    void shouldAllowType() {
        final TypeValidator validator = new TypeValidator(List.of(String.class), List.of(), false, false, () -> null);

        assertTrue(validator.validate(String.class));
        assertFalse(validator.validate(String[].class));
    }

    @Test
    void shouldAllowTypeAsArray() {
        final TypeValidator validator = new TypeValidator(List.of(String.class), List.of(), false, true, () -> null);

        assertTrue(validator.validate(String[].class));
    }

    @Test
    void shouldAllowPackage() {
        final TypeValidator validator = new TypeValidator(List.of(), List.of("java.lang"), false, false, () -> null);

        assertTrue(validator.validate(String.class));
        assertFalse(validator.validate(String[].class));
    }

    @Test
    void shouldAllowPackageAsArray() {
        final TypeValidator validator = new TypeValidator(List.of(String.class), List.of(), false, true, () -> null);

        assertTrue(validator.validate(String[].class));
    }

    @Nested
    class ListOperations {
        @Test
        void addType() {
            final List<Class<?>> classes = mock(List.class);
            final List<String> packages = mock(List.class);

            final TypeValidator validator = new TypeValidator(classes, packages, false, false, () -> null);
            validator.addClass(String.class);

            verify(classes).addAll(eq(List.of(String.class)));
            verifyNoInteractions(packages);
        }

        @Test
        void addPackage() {
            final List<Class<?>> classes = mock(List.class);
            final List<String> packages = mock(List.class);

            final TypeValidator validator = new TypeValidator(classes, packages, false, false, () -> null);
            validator.addPackage("java.lang");

            verify(packages).add(eq("java.lang"));
            verifyNoInteractions(classes);
        }

        @Test
        void removeType() {
            final List<Class<?>> classes = mock(List.class);
            final List<String> packages = mock(List.class);

            final TypeValidator validator = new TypeValidator(classes, packages, false, false, () -> null);
            validator.removeClass(String.class);

            verify(classes).remove(eq(String.class));
            verifyNoInteractions(packages);
        }

        @Test
        void removePackage() {
            final List<Class<?>> classes = mock(List.class);
            final List<String> packages = mock(List.class);

            final TypeValidator validator = new TypeValidator(classes, packages, false, false, () -> null);
            validator.removePackage("java.lang");

            verify(packages).remove(eq("java.lang"));
            verifyNoInteractions(classes);
        }
    }
}