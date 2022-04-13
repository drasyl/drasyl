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
                                        Object[] titles,
                                        Object[] values,
                                        boolean append) {
        Writer writer = writers.get(filename);
        if (writer == null) {
            try {
                final String pathname = "./" + filename + ".csv";
                final boolean exists = new File(pathname).exists();
                writer = new FileWriter(pathname, append);
                if (!exists) {
                    boolean first = true;
                    for (Object title : titles) {
                        if (!first) {
                            writer.append(',');
                        }
                        writer.append('"' + title.toString() + '"');
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
            for (Object value : values) {
                if (!first) {
                    writer.append(',');
                }
                writer.append('"' + value.toString() + '"');
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
