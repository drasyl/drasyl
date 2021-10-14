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
package org.drasyl.crypto.sodium;

import org.drasyl.crypto.loader.LibraryLoader;
import org.drasyl.crypto.loader.LoaderException;
import org.drasyl.crypto.loader.NativeLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.drasyl.crypto.loader.LibraryLoader.Mode.PREFER_SYSTEM;

public class DrasylSodium extends Sodium {
    public DrasylSodium() throws LoaderException {
        this(PREFER_SYSTEM);
    }

    public DrasylSodium(final LibraryLoader.Mode loadingMode) throws LoaderException {
        new LibraryLoader(getClassesToRegister()).loadLibrary(loadingMode, "sodium");
        register();
    }

    public DrasylSodium(final File libFile) throws LoaderException {
        try {
            for (final Class clazz : getClassesToRegister()) {
                NativeLoader.loadLibraryFromFileSystem(libFile.getAbsolutePath(), clazz);
            }
            register();
        }
        catch (final Exception e) {
            throw new LoaderException("Could not load local library due to: ", e);
        }
    }

    public static List<Class> getClassesToRegister() {
        final List<Class> classes = new ArrayList<>();
        classes.add(Sodium.class);
        return classes;
    }
}
