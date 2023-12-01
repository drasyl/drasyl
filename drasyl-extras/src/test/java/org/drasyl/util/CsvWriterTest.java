package org.drasyl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

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
