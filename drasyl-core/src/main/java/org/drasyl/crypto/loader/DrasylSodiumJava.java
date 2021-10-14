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

import com.goterl.lazysodium.utils.LibraryLoader;
import com.goterl.resourceloader.ResourceLoaderException;
import org.drasyl.crypto.sodium.DrasylSodium;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.goterl.lazysodium.utils.LibraryLoader.Mode.PREFER_SYSTEM;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class DrasylSodiumJava extends DrasylSodium {
    public DrasylSodiumJava() {
        this(PREFER_SYSTEM);
    }

    public DrasylSodiumJava(final LibraryLoader.Mode loadingMode) {
        new DrasylLibraryLoader(getClassesToRegister()).loadLibrary(loadingMode, "sodium");
        register();
    }

    public DrasylSodiumJava(final File libFile) {
        try {
            for (final Class clazz : getClassesToRegister()) {
                NativeLoader.loadLibraryFromFileSystem(libFile.getAbsolutePath(), clazz);
            }
            register();
        }
        catch (final Exception e) {
            throw new ResourceLoaderException("Could not load local library due to: ", e);
        }
    }

    private File copyToTempDir(final File file, final File outputDir) throws IOException {
        final File resourceCopiedToTempFolder = new File(outputDir, file.getName());
        Files.copy(file.toPath(), resourceCopiedToTempFolder.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES,
                NOFOLLOW_LINKS);
        return resourceCopiedToTempFolder;
    }

    public static List<Class> getClassesToRegister() {
        final List<Class> classes = new ArrayList<>();
        classes.add(DrasylSodium.class);
        return classes;
    }
}
