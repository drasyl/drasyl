/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

class CsvWriterTest {
    @Test
    void test(@TempDir final Path dir) throws IOException {
        final Path path = dir.resolve("my-file.csv");

        try (CsvWriter writer = new CsvWriter(path)) {
            writer.write("foo", "bar", "baz");
            writer.write("foo2", "bar2", "baz2");
            writer.write(1, true, 2.0);
        }
    }

    @Test
    void test2(@TempDir final Path dir) throws IOException {
        final Path path = dir.resolve("my-file.csv");

        try (CsvWriter writer = new CsvWriter(path, "column1", "column2")) {
            writer.write("foo", "bar");
        }

        System.out.println();
    }

    @Test
    void test3(@TempDir final Path dir) throws IOException {
        final Path path = dir.resolve("my-file.csv");

        try (CsvWriter writer = new CsvWriter(path, "column1", "column2")) {
            writer.write("foo", "bar");
            writer.write("foo2", "bar2", "baz2");
        }

        System.out.println();
    }
}
