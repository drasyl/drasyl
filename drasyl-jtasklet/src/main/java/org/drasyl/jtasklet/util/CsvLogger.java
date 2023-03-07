package org.drasyl.jtasklet.util;

import org.drasyl.jtasklet.LoggableRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.concurrent.locks.ReentrantLock;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class CsvLogger {
    public static final long PID = ProcessHandle.current().pid();
    private final ReentrantLock lock;
    private final FileWriter writer;
    private boolean headerWritten;

    public CsvLogger(final String fileName) {
        try {
            lock = new ReentrantLock(true);
            headerWritten = new File(fileName).exists();
            writer = new FileWriter(fileName, true);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void log(final LoggableRecord loggableRecord) {
        lock.lock();
        try {
            // header
            if (!headerWritten) {
                headerWritten = true;
                escapedWrite(writer, "pid");
                writer.append(",");
                escapedWrite(writer, "time");
                for (final Object title : loggableRecord.logTitles()) {
                    writer.append(",");
                    escapedWrite(writer, title);
                }
                writer.append('\n');
            }
            writer.flush();

            // row
            escapedWrite(writer, PID);
            writer.append(",");
            escapedWrite(writer, RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
            for (final Object title : loggableRecord.logValues()) {
                writer.append(",");
                escapedWrite(writer, title);
            }
            writer.append('\n');
            writer.flush();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
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
