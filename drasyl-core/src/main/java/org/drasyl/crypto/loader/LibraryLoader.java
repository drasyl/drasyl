/*
 * Copyright (c) Terl Tech Ltd • 01/04/2021, 12:31 • goterl.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.drasyl.crypto.loader;

import java.util.ArrayList;
import java.util.List;

public class LibraryLoader {
    private final List<Class> classes = new ArrayList<>();

    public LibraryLoader(List<Class> classesToRegister) {
        classes.addAll(classesToRegister);
    }

    public void loadSystemLibrary(String library) {
        SharedLibraryLoader.get().loadSystemLibrary(library, classes);
    }

    private void loadBundledLibrary() {
        String pathInJar = com.goterl.lazysodium.utils.LibraryLoader.getSodiumPathInResources();
        SharedLibraryLoader.get().load(pathInJar, classes);
    }

    public void loadLibrary(com.goterl.lazysodium.utils.LibraryLoader.Mode mode,
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
