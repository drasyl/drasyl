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
package org.drasyl.node;

import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class to read/write a {@link Identity} from/to a file.
 */
@SuppressWarnings("java:S2974")
public class IdentityFile {
    protected static final Charset CHARSET = UTF_8;
    static final char SECTION_BEGIN = '[';
    static final char SECTION_END = ']';
    static final char COMMENT = '[';
    static final String SECTION_IDENTITY = "Identity";
    static final String PROPERTY_SECRET_KEY = "SecretKey";
    static final String PROPERTY_POW_KEY = "ProofOfWork";

    private IdentityFile() {
        // util class
    }

    public static void writeTo(final OutputStream out,
                               final Identity identity) throws IOException {
        try {
            out.write("[Identity]\n".getBytes(CHARSET));
            out.write(("SecretKey = " + identity.getIdentitySecretKey().toUnmaskedString() + "\n").getBytes(CHARSET));
            out.write(("ProofOfWork = " + identity.getProofOfWork()).getBytes(CHARSET));
        }
        finally {
            out.close();
        }
    }

    @SuppressWarnings("java:S1943")
    public static void writeTo(final File file, final Identity identity) throws IOException {
        writeTo(new FileOutputStream(file), identity);
    }

    @SuppressWarnings("java:S1943")
    public static void writeTo(final String pathname,
                               final Identity identity) throws IOException {
        writeTo(new FileOutputStream(pathname), identity);
    }

    @SuppressWarnings("java:S1943")
    public static void writeTo(final Path path, final Identity identity) throws IOException {
        writeTo(path.toFile(), identity);
    }

    @SuppressWarnings({ "java:S134", "java:S1541", "java:S3776" })
    public static Identity readFrom(final InputStream in) throws IOException {
        IdentitySecretKey secretKey = null;
        ProofOfWork proofOfWork = null;

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in, CHARSET))) {
            String section = null;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.charAt(0) == SECTION_BEGIN && line.charAt(line.length() - 1) == SECTION_END) {
                    section = parseSectionLine(line);
                }
                else if (line.charAt(0) != COMMENT && line.indexOf('=') != -1) {
                    final Pair<String, String> property = parsePropertyLine(line);

                    if (SECTION_IDENTITY.equals(section)) {
                        if (PROPERTY_SECRET_KEY.equals(property.first())) {
                            secretKey = IdentitySecretKey.of(property.second());
                        }
                        else if (PROPERTY_POW_KEY.equals(property.first())) {
                            proofOfWork = ProofOfWork.of(property.second());
                        }
                    }
                }

                if (secretKey != null && proofOfWork != null) {
                    // we have everything we need. stop reading input.
                    break;
                }
            }
        }
        catch (final IllegalArgumentException e) {
            throw new IOException("INI file contain invalid 'SecretKey' or 'ProofOfWork' value.", e);
        }

        if (secretKey == null || proofOfWork == null) {
            throw new IOException("INI file does not contain a section 'Identity' with the properties 'SecretKey' and 'ProofOfWork'.");
        }

        return Identity.of(proofOfWork, secretKey);
    }

    public static Identity readFrom(final File file) throws IOException {
        return readFrom(new FileInputStream(file));
    }

    public static Identity readFrom(final Path path) throws IOException {
        return readFrom(path.toFile());
    }

    public static Identity readFrom(final String pathname) throws IOException {
        return readFrom(new File(pathname));
    }

    private static String parseSectionLine(final String line) {
        return line.substring(1, line.length() - 1).trim();
    }

    private static Pair<String, String> parsePropertyLine(final String line) {
        final int idx = line.indexOf('=');
        final String name = line.substring(0, idx).trim();
        final String value = line.substring(idx + 1).trim();
        return Pair.of(name, value);
    }
}
