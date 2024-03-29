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
package org.drasyl.crypto.loader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeLoaderTest {
    @Test
    void testLoadLibraryIllegalPath() {
        assertThrows(IOException.class, () -> NativeLoader.loadLibraryFromJar("test.so", NativeLoader.class));
    }

    @Test
    void testLoadLibraryIllegalPrefix() {
        assertThrows(IOException.class, () -> NativeLoader.loadLibraryFromJar("/t", NativeLoader.class));
    }

    @Test
    void testLoadLibraryNonExistentPath() {
        assertThrows(IOException.class, () -> NativeLoader.loadLibraryFromJar("/test.so", NativeLoader.class));
    }

    @Test
    void testLoadLibraryNonExistentPath2() {
        assertThrows(IOException.class, () -> NativeLoader.loadLibraryFromFileSystem("/test.so", NativeLoader.class));
    }

    @Test
    void testLoadLibraryNullPath() {
        assertThrows(IOException.class, () -> NativeLoader.loadLibraryFromJar(null, NativeLoader.class));
    }
}
