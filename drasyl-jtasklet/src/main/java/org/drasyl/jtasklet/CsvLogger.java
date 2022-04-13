package org.drasyl.jtasklet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CsvLogger {
    private static final Map<String, Writer> writers = new HashMap<>();

    public static synchronized void log(final String filename,
                                        Map<String, Object> columns,
                                        boolean append) {
        Writer writer = writers.get(filename);
        if (writer == null) {
            try {
                final boolean exists = new File("./" + filename + ".csv").exists();
                writer = new FileWriter("./" + filename + ".csv", append);
                if (!exists) {
                    boolean first = true;
                    for (Entry<String, Object> entry : columns.entrySet()) {
                        if (!first) {
                            writer.append(',');
                        }
                        writer.append('"' + entry.getKey() + '"');
                        first = false;
                    }
                    writer.append('\n');
                    writers.put(filename, writer);
                }
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            boolean first = true;
            for (Entry<String, Object> entry : columns.entrySet()) {
                if (!first) {
                    writer.append(',');
                }
                writer.append('"' + entry.getValue().toString() + '"');
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
