package org.drasyl.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

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
        };
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
