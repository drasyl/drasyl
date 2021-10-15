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
package org.drasyl.crypto.loader;

import com.sun.jna.Native;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;

/**
 * Based on: https://github.com/adamheinrich/native-utils
 */
public class NativeLoader {
    public static final String NATIVE_FOLDER_PATH_PREFIX = "nativeloader";
    /**
     * The minimum length a prefix for a file has to have according to {@link
     * File#createTempFile(String, String)}}.
     */
    private static final int MIN_PREFIX_LENGTH = 3;
    private static File temporaryDir;

    /**
     * Private constructor - this class will never be instanced
     */
    private NativeLoader() {
    }

    /**
     * Loads library from current JAR archive
     * <p>
     * The file is copied into system temporary directory and then loaded. The temporary file is
     * deleted after exiting. Method uses String as filename because the pathname is "abstract", not
     * system-dependent.
     *
     * @param path The path of file as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws LoaderException If library could not be loaded
     */
    public static synchronized void loadLibraryFromJar(final String path,
                                                       final Class clazz) throws LoaderException {
        loadLibrary(path, clazz, true);
    }

    /**
     * Loads library from current file system
     * <p>
     * The file is copied into system temporary directory and then loaded. The temporary file is
     * deleted after exiting. Method uses String as filename because the pathname is "abstract", not
     * system-dependent.
     *
     * @param path The path of file as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws LoaderException If library could not be loaded
     */
    public static synchronized void loadLibraryFromFileSystem(final String path,
                                                              final Class clazz) throws LoaderException {
        loadLibrary(path, clazz, false);
    }

    private static synchronized void loadLibrary(final String path,
                                                 final Class clazz,
                                                 final boolean fromJar) throws LoaderException {
        if (null == path || !path.startsWith("/")) {
            throw new LoaderException("The path has to be absolute (start with '/'): " + path);
        }

        // Obtain filename from path
        final String[] parts = path.split("/");
        final String filename = (parts.length > 1) ? parts[parts.length - 1] : null;

        // Check if the filename is okay
        if (filename == null || filename.length() < MIN_PREFIX_LENGTH) {
            throw new LoaderException("The filename has to be at least 3 characters long.");
        }

        // Prepare temporary file
        if (temporaryDir == null) {
            try {
                temporaryDir = createTempDirectory();
            }
            catch (final IOException e) {
                throw new LoaderException(e);
            }
            temporaryDir.deleteOnExit();
        }

        final File temp = new File(temporaryDir, filename);

        if (fromJar) {
            try (final InputStream is = NativeLoader.class.getResourceAsStream(path)) {
                Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (final IOException e) {
                temp.delete(); // NOSONAR
                throw new LoaderException(e);
            }
            catch (final NullPointerException e) {
                temp.delete(); // NOSONAR
                throw new LoaderException("File " + path + " was not found inside JAR.");
            }
        }
        else {
            try (final InputStream is = new FileInputStream(path)) {
                Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (final IOException e) {
                temp.delete(); // NOSONAR
                throw new LoaderException(e);
            }
        }

        try {
            Native.register(clazz, temp.getAbsolutePath());
        }
        finally {
            if (isPosixCompliant()) {
                // Assume POSIX compliant file system, can be deleted after loading
                temp.deleteOnExit(); // NOSONAR
            }
            else {
                // Assume non-POSIX, and don't delete until last file descriptor closed
                temp.deleteOnExit();
            }
        }
    }

    private static boolean isPosixCompliant() {
        try {
            return FileSystems.getDefault()
                    .supportedFileAttributeViews()
                    .contains("posix");
        }
        catch (final FileSystemNotFoundException
                | ProviderNotFoundException
                | SecurityException e) {
            return false;
        }
    }

    private static File createTempDirectory() throws IOException {
        final String tempDir = System.getProperty("java.io.tmpdir");
        final File generatedDir = new File(tempDir, NativeLoader.NATIVE_FOLDER_PATH_PREFIX + System.nanoTime());

        if (!generatedDir.mkdir()) {
            throw new IOException("Failed to create temp directory " + generatedDir.getName());
        }

        return generatedDir;
    }
}

