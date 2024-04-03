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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public class CsvWriter implements AutoCloseable {
    private final FileWriter fileWriter;
    private final String[] columns;

    public CsvWriter(final FileWriter fileWriter, final String... columns) throws IOException {
        this.fileWriter = requireNonNull(fileWriter);
        this.columns = requireNonNull(columns);
        if (columns.length > 0) {
            write(columns);
        }
    }

    public CsvWriter(final FileWriter fileWriter) throws IOException {
        this(fileWriter, new String[0]);
    }

    public CsvWriter(final File file, final String... columns) throws IOException {
        this(new FileWriter(file), columns);
    }

    public CsvWriter(final Path path, final String... columns) throws IOException {
        this(path.toFile(), columns);
    }

    public CsvWriter(final File file) throws IOException {
        this(new FileWriter(file));
    }

    public CsvWriter(final Path path) throws IOException {
        this(path.toFile());
    }

    public void write(final String... values) throws IOException {
        if (columns.length != 0 && columns.length != values.length) {
            throw new IllegalArgumentException("CSV file has " + columns.length + " column(s) but values have " + values.length + " column(s)");
        }
        fileWriter.append(String.join(",", values));
        fileWriter.append("\n");
        fileWriter.flush();
    }

    public void write(final Object... values) throws IOException {
        write(Arrays.stream(values).map(Object::toString).toArray(String[]::new));
    }

    @Override
    public void close() throws IOException {
        fileWriter.close();
    }
}
