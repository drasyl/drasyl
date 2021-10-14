/*
 * Copyright (c) Terl Tech Ltd • 01/04/2021, 12:31 • goterl.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.drasyl.crypto.loader;

import com.goterl.resourceloader.ResourceLoaderException;
import com.sun.jna.Native;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class DrasylSharedLibraryLoader extends DrasylResourceLoader {
    private final Object lock = new Object();

    private DrasylSharedLibraryLoader() {
        super();
    }

    /**
     * Get an instance of the loader.
     *
     * @return Returns this loader instantiated.
     */
    public static DrasylSharedLibraryLoader get() {
        return DrasylSharedLibraryLoader.SingletonHelper.INSTANCE;
    }

    public void loadSystemLibrary(final String libraryName, final Class clzz) {
        loadSystemLibrary(libraryName, Collections.singletonList(clzz));
    }

    public void loadSystemLibrary(final String libraryName, final List<Class> classes) {
        registerLibraryWithClasses(libraryName, classes);
    }

    public File load(final String relativePath, final Class clzz) {
        return load(relativePath, Collections.singletonList(clzz));
    }

    public File load(final String relativePath, final List<Class> classes) {
        synchronized (lock) {
            try {
                final File library = copyToTempDirectory(relativePath, classes.get(0));
                setPermissions(library);
                if (library.isDirectory()) {
                    throw new IOException("Please supply a relative path to a file and not a directory.");
                }
                registerLibraryWithClasses(library.getAbsolutePath(), classes);
                requestDeletion(library);
                return library;
            }
            catch (final IOException e) {
                final String message = String.format(
                        "Failed to load the bundled library from resources by relative path (%s)",
                        relativePath
                );
                throw new ResourceLoaderException(message, e);
            }
            catch (final URISyntaxException e) {
                final String message = String.format(
                        "Finding the library from path (%s) failed!",
                        relativePath
                );
                throw new ResourceLoaderException(message, e);
            }
        }
    }

    private void registerLibraryWithClasses(final String absolutePath, final List<Class> classes) {
        requireNonNull(absolutePath, "Please supply an absolute path.");
        synchronized (lock) {
            for (final Class clzz : classes) {
                Native.register(clzz, absolutePath);
            }
        }
    }

    private static class SingletonHelper {
        private static final DrasylSharedLibraryLoader INSTANCE = new DrasylSharedLibraryLoader();
    }
}
