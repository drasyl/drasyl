package org.drasyl.jtasklet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class CsvLogger {
    private static final Map<String, Writer> writers = new HashMap<>();

    public static synchronized void log(final String filename,
                                        Map<String, Object> columns,
                                        boolean append) {
        Writer writer = writers.get(filename);
        if (writer == null) {
            try {
                writer = new FileWriter("./" + filename + ".csv", append);
                boolean first = true;
                for (String column : columns.keySet()) {
                    if (!first) {
                        writer.append(',');
                    }
                    writer.append('"' + column + '"');
                    first = false;
                }
                writer.append('\n');
                writers.put(filename, writer);
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            boolean first = true;
            for (Object column : columns.values()) {
                if (!first) {
                    writer.append(',');
                }
                writer.append('"' + column.toString() + '"');
                first = false;
            }
            writer.append('\n');
            writer.flush();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
