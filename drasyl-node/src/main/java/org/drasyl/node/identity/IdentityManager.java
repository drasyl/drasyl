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
package org.drasyl.node.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.drasyl.identity.Identity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.drasyl.node.JSONUtil.JACKSON_READER;
import static org.drasyl.node.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.PathUtil.hasPosixSupport;

/**
 * Utility class for writing an {@link Identity} to a {@link Path} and vice versa.
 */
public final class IdentityManager {
    private IdentityManager() {
        // util class
    }

    /**
     * Returns <code>true</code> if the identity file <code>path</code> exists. Otherwise
     * <code>false</code> is returned.
     *
     * @param path path to identity file
     * @return {@code true} if file exists
     */
    public static boolean isIdentityFilePresent(final Path path) {
        return path.toFile().exists() && path.toFile().isFile();
    }

    /**
     * Reads the identity from {@code code}. Throws {@code IOException} if file cannot be read or
     * file has unexpected content.
     *
     * @param path path to identity file
     * @return The identity contained in the file
     * @throws IOException if identity could not be read from file or has an invalid proof of work
     */
    public static Identity readIdentityFile(final Path path) throws IOException {
        try {
            // check if file permissions are too open
            if (hasPosixSupport(path)) {
                final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
                if (!Collections.disjoint(permissions, Set.of(GROUP_EXECUTE, GROUP_WRITE, GROUP_READ, OTHERS_EXECUTE, OTHERS_WRITE, OTHERS_READ))) {
                    throw new IOException("Unprotected private key: It is required that your identity file '" + path + "' is NOT accessible by others.'");
                }
            }

            final Identity identity = JACKSON_READER.readValue(path.toFile(), Identity.class);

            if (!identity.isValid()) {
                throw new IOException("Identity from file '" + path + "' has an invalid proof of work.");
            }

            return identity;
        }
        catch (final JsonProcessingException e) {
            throw new IOException("Unable to load identity from file '" + path + "': ", e);
        }
    }

    /**
     * Writes the identity {@code identity} to the file {@code path}. Attention: If {@code path}
     * already contains an identity, it will be overwritten without warning.
     *
     * @param path     path where the identity should be written to
     * @param identity this identity is written to the file
     * @throws IOException if the identity could not be written to the file
     */
    public static void writeIdentityFile(final Path path,
                                         final Identity identity) throws IOException {
        final File file = path.toFile();

        if (Files.isDirectory(path) || (file.getParentFile() != null && !file.getParentFile().exists())) {
            throw new IOException("Identity path '" + path + "' is a directory or path does not exist");
        }
        else if (file.exists() && !file.canWrite()) {
            throw new IOException("Identity path '" + path + "' is not writable");
        }
        else {
            // ensure that file permissions will not be too open
            if (hasPosixSupport(path) && file.createNewFile()) {
                Files.setPosixFilePermissions(path, Set.of(OWNER_READ, OWNER_WRITE));
            }

            JACKSON_WRITER.with(new DefaultPrettyPrinter()).writeValue(file, identity);
        }
    }

    /**
     * Deletes the identity file specified in the configuration.
     * <p>
     * ATTENTION: Messages directed to the present identity can then no longer be decrypted and
     * read. This step is irreversible. Should only be used if the present identity should never be
     * used again!
     *
     * @throws IOException if identity file could not be deleted
     */
    public static void deleteIdentityFile(final Path path) throws IOException {
        final File file = path.toFile();

        if (!file.exists()) {
            // nothing to do
            return;
        }

        Files.delete(path);
    }
}
