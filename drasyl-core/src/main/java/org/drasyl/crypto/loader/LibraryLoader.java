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
import com.sun.jna.Platform;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Helper class to load the libsodium library from the preferred location.
 */
@UnstableApi
public class LibraryLoader {
    public static final String PREFER_SYSTEM = "pref_system";
    public static final String PREFER_BUNDLED = "pref_bundled";
    public static final String BUNDLED_ONLY = "bundled_only";
    public static final String SYSTEM_ONLY = "system_only";
    private static final Logger LOG = LoggerFactory.getLogger(LibraryLoader.class);
    private final Class clazz;

    public LibraryLoader(final Class classToRegister) {
        clazz = requireNonNull(classToRegister);
    }

    public static String getSodiumPlatformDependentPath() {
        final boolean is64Bit = Native.POINTER_SIZE == 8;
        if (Platform.isWindows()) {
            if (is64Bit) {
                return getPath("windows64", "libsodium.dll");
            }
            return getPath("windows", "libsodium.dll");
        }
        if (Platform.isMac()) {
            // check for Apple Silicon
            if (Platform.isARM()) {
                return getPath("mac/aarch64", "libsodium.dylib");
            }
            return getPath("mac/intel", "libsodium.dylib");
        }
        if (Platform.isARM()) {
            if (is64Bit) {
                return getPath("arm64", "libsodium.so");
            }
            return getPath("armv6", "libsodium.so");
        }
        if (Platform.isLinux()) {
            if (is64Bit) {
                return getPath("linux64", "libsodium.so");
            }
            return getPath("linux", "libsodium.so");
        }

        return null;
    }

    public static String getSodiumPathInResources() throws IOException {
        final String platformPath = getSodiumPlatformDependentPath();

        if (platformPath == null) {
            final String message = String.format("Unsupported platform: %s/%s", System.getProperty("os.name"),
                    System.getProperty("os.arch"));
            throw new IOException(message);
        }

        return getPath("/META-INF", "native", "libsodium", platformPath);
    }

    private static String getPath(final String... parts) {
        final String separator = "/";
        return String.join(separator, parts);
    }

    public void loadSystemLibrary(final String library) throws IOException {
        try {
            Native.register(clazz, library);
        }
        catch (final UnsatisfiedLinkError e) {
            throw new IOException(e);
        }
    }

    private void loadBundledLibrary() throws IOException {
        final String pathInJar = LibraryLoader.getSodiumPathInResources();

        try {
            NativeLoader.loadLibraryFromJar(pathInJar, clazz);
        }
        catch (final UnsatisfiedLinkError e) {
            throw new IOException("Could not load lib from " + pathInJar, e);
        }
    }

    public void loadLibrary(final String mode,
                            final String systemFallBack) throws IOException {
        switch (mode) {
            case PREFER_SYSTEM:
                try {
                    loadSystemLibrary(systemFallBack);
                    LOG.debug("Loaded system sodium library.");
                }
                catch (final IOException suppressed) {
                    // Attempt to load the bundled
                    loadBundledLibrary();
                    LOG.debug("Loaded bundled sodium library: {}", LibraryLoader::getSodiumPlatformDependentPath);
                }
                break;
            case PREFER_BUNDLED:
                try {
                    loadBundledLibrary();
                    LOG.debug("Loaded bundled sodium library: {}", LibraryLoader::getSodiumPlatformDependentPath);
                }
                catch (final IOException suppressed) {
                    loadSystemLibrary(systemFallBack);
                    LOG.debug("Loaded system sodium library.");
                }
                break;
            case BUNDLED_ONLY:
                loadBundledLibrary();
                LOG.debug("Loaded bundled sodium library: {}", LibraryLoader::getSodiumPlatformDependentPath);
                break;
            case SYSTEM_ONLY:
                loadSystemLibrary(systemFallBack);
                LOG.debug("Loaded system sodium library.");
                break;
            default:
                throw new IOException("Unsupported mode: " + mode);
        }
    }
}
