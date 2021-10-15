/*
 * Copyright (c) Terl Tech Ltd • 01/04/2021, 12:31 • goterl.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.drasyl.crypto.loader;

import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.util.ArrayList;
import java.util.List;

public class LibraryLoader {
    public static final String PREFER_SYSTEM = "pref_system";
    public static final String PREFER_BUNDLED = "pref_bundled";
    public static final String BUNDLED_ONLY = "bundled_only";
    public static final String SYSTEM_ONLY = "system_only";
    private final List<Class> classes = new ArrayList<>();

    public LibraryLoader(final List<Class> classesToRegister) {
        classes.addAll(classesToRegister);
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

    public static String getSodiumPathInResources() throws LoaderException {
        final String platformPath = getSodiumPlatformDependentPath();
        final String path = getPath("/META-INF", "native", "libsodium", platformPath);

        if (platformPath == null) {
            final String message = String.format("Unsupported platform: %s/%s", System.getProperty("os.name"),
                    System.getProperty("os.arch"));
            throw new LoaderException(message);
        }

        return path;
    }

    private static String getPath(final String... parts) {
        final String separator = "/";
        return String.join(separator, parts);
    }

    public void loadSystemLibrary(final String library) throws LoaderException {
        for (final Class clazz : classes) {
            try {
                Native.register(clazz, library);
            }
            catch (final UnsatisfiedLinkError e) {
                throw new LoaderException(e);
            }
        }
    }

    private void loadBundledLibrary() throws LoaderException {
        final String pathInJar = LibraryLoader.getSodiumPathInResources();

        for (final Class clazz : classes) {
            NativeLoader.loadLibraryFromJar(pathInJar, clazz);
        }
    }

    public void loadLibrary(final String mode,
                            final String systemFallBack) throws LoaderException {
        switch (mode) {
            case PREFER_SYSTEM:
                try {
                    loadSystemLibrary(systemFallBack);
                }
                catch (final LoaderException suppressed) {
                    // Attempt to load the bundled
                    loadBundledLibrary();
                }
                break;
            case PREFER_BUNDLED:
                try {
                    loadBundledLibrary();
                }
                catch (final LoaderException suppressed) {
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
}
