package org.drasyl.jtasklet.util;

import org.drasyl.jtasklet.TaskRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class CsvLogger {
    private final FileWriter writer;
    private boolean headerWritten;

    public CsvLogger(final String fileName) {
        try {
            headerWritten = new File(fileName).exists();
            writer = new FileWriter(fileName, true);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void log(final TaskRecord taskRecord) {
        try {
            // header
            if (!headerWritten) {
                headerWritten = true;
                escapedWrite(writer, "time");
                for (final Object title : taskRecord.logTitles()) {
                    writer.append(",");
                    escapedWrite(writer, title);
                }
                writer.append('\n');
            }

            // row
            escapedWrite(writer, RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
            for (final Object title : taskRecord.logValues()) {
                writer.append(",");
                escapedWrite(writer, title);
            }
            writer.append('\n');
            writer.flush();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void escapedWrite(final FileWriter writer,
                              final CharSequence value) throws IOException {
        writer.append("\"");
        writer.append(value);
        writer.append("\"");
    }

    private void escapedWrite(final FileWriter writer, final Object value) throws IOException {
        escapedWrite(writer, value != null ? value.toString() : "");
    }
}
