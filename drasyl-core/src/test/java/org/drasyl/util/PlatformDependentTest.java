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
import org.junit.jupiter.api.function.Executable;

import static org.drasyl.util.PlatformDependent.javaSpecificationVersion;
import static org.drasyl.util.PlatformDependent.javaVersion;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PlatformDependentTest {
    @Nested
    class JavaVersion {
        @Test
        void shouldReturnVersion() {
            assertThat(javaVersion(), greaterThan(0f));
        }
    }

    @Nested
    class UnsafeStaticFieldOffsetSupported {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldNotThrowException() {
            assertDoesNotThrow((Executable) PlatformDependent::unsafeStaticFieldOffsetSupported);
        }
    }

    @Nested
    class JavaSpecificationVersion {
        @Test
        void shouldReturnVersion() {
            assertThat(javaSpecificationVersion(), greaterThan(0f));
        }
    }
}
