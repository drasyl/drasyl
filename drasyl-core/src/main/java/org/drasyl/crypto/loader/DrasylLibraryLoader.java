/*
 * Copyright (c) Terl Tech Ltd • 01/04/2021, 12:31 • goterl.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.drasyl.crypto.loader;

import com.goterl.lazysodium.utils.LibraryLoader;
import com.goterl.lazysodium.utils.LibraryLoadingException;
import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DrasylLibraryLoader {
    private final List<Class> classes = new ArrayList<>();

    public DrasylLibraryLoader(final List<Class> classesToRegister) {
        classes.addAll(classesToRegister);
    }

    public void loadSystemLibrary(final String library) throws IOException {
        for (final Class clazz : classes) {
            NativeLoader.loadLibraryFromFileSystem(library, clazz);
        }
    }

    private void loadBundledLibrary() throws IOException {
        final String pathInJar = DrasylLibraryLoader.getSodiumPathInResources();

        for (final Class clazz : classes) {
            NativeLoader.loadLibraryFromJar(pathInJar, clazz);
        }
    }

    public void loadLibrary(final LibraryLoader.Mode mode,
                            final String systemFallBack) {
        try {
            switch (mode) {
                case PREFER_SYSTEM:
                    try {
                        loadSystemLibrary(systemFallBack);
                    }
                    catch (final Throwable suppressed) {
                        // Attempt to load the bundled
                        loadBundledLibrary();
                    }
                    break;
                case PREFER_BUNDLED:
                    try {
                        loadBundledLibrary();
                    }
                    catch (final Throwable suppressed) {
                        loadSystemLibrary(systemFallBack);
                    }
                    break;
                case BUNDLED_ONLY:
                    loadBundledLibrary();
                    break;
                case SYSTEM_ONLY:
                    loadSystemLibrary(systemFallBack);
                    break;
                default:
                    throw new IllegalStateException("Unsupported mode: " + mode);
            }
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getSodiumPathInResources() {
        final boolean is64Bit = Native.POINTER_SIZE == 8;
        if (Platform.isWindows()) {
            if (is64Bit) {
                return getPath("/META-INF/native/libsodium/windows64", "libsodium.dll");
            }
            else {
                return getPath("/META-INF/native/libsodium/windows", "libsodium.dll");
            }
        }
        if (Platform.isMac()) {
            // check for Apple Silicon
            if (Platform.isARM()) {
                return getPath("/META-INF/native/libsodium/mac/aarch64", "libsodium.dylib");
            }
            else {
                return getPath("/META-INF/native/libsodium/mac/intel", "libsodium.dylib");
            }
        }
        if (Platform.isARM()) {
            return getPath("/META-INF/native/libsodium/armv6", "libsodium.so");
        }
        if (Platform.isLinux()) {
            if (is64Bit) {
                return getPath("/META-INF/native/libsodium/linux64", "libsodium.so");
            }
            else {
                return getPath("/META-INF/native/libsodium/linux", "libsodium.so");
            }
        }

        final String message = String.format("Unsupported platform: %s/%s", System.getProperty("os.name"),
                System.getProperty("os.arch"));
        throw new LibraryLoadingException(message);
    }

    private static String getPath(final String folder, final String name) {
        final String separator = "/";
        return folder + separator + name;
    }
}
