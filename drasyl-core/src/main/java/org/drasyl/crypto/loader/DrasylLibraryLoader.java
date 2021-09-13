/*
 * Copyright (c) Terl Tech Ltd • 01/04/2021, 12:31 • goterl.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.drasyl.crypto.loader;

import com.goterl.lazysodium.utils.LibraryLoader;

import java.util.ArrayList;
import java.util.List;

public class DrasylLibraryLoader {
    private final List<Class> classes = new ArrayList<>();

    public DrasylLibraryLoader(List<Class> classesToRegister) {
        classes.addAll(classesToRegister);
    }

    public void loadSystemLibrary(String library) {
        DrasylSharedLibraryLoader.get().loadSystemLibrary(library, classes);
    }

    private void loadBundledLibrary() {
        String pathInJar = LibraryLoader.getSodiumPathInResources();
        DrasylSharedLibraryLoader.get().load(pathInJar, classes);
    }

    public void loadLibrary(LibraryLoader.Mode mode,
                            String systemFallBack) {
        switch (mode) {
            case PREFER_SYSTEM:
                try {
                    loadSystemLibrary(systemFallBack);
                }
                catch (Throwable suppressed) {
                    // Attempt to load the bundled
                    loadBundledLibrary();
                }
                break;
            case PREFER_BUNDLED:
                try {
                    loadBundledLibrary();
                }
                catch (Throwable suppressed) {
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
