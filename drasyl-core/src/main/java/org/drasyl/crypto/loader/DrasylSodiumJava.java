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

import com.goterl.lazysodium.SodiumJava;
import com.goterl.resourceloader.ResourceLoader;
import com.goterl.resourceloader.ResourceLoaderException;
import com.goterl.resourceloader.SharedLibraryLoader;
import com.sun.jna.Native;
import org.drasyl.crypto.loader.LibraryLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static com.goterl.lazysodium.utils.LibraryLoader.Mode.PREFER_SYSTEM;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class DrasylSodiumJava extends SodiumJava {
    public DrasylSodiumJava() {
        this(PREFER_SYSTEM);
    }

    public DrasylSodiumJava(com.goterl.lazysodium.utils.LibraryLoader.Mode loadingMode) {
        new LibraryLoader(getClassesToRegister()).loadLibrary(loadingMode, "sodium");
        onRegistered();
    }

    public DrasylSodiumJava(File libFile) {
        try {
            File tempDir = ResourceLoader.createMainTempDirectory();
            tempDir.mkdirs();

            // We want to copy this to a temp dir to have the right to change permissions
            File library = copyToTempDir(libFile, tempDir);
            SharedLibraryLoader.get().setPermissions(library);
            if (library.isDirectory()) {
                throw new ResourceLoaderException("Please supply a relative path to a file and not a directory.");
            }
            for (Class clzz : getClassesToRegister()) {
                Native.register(clzz, library.getAbsolutePath());
            }
            SharedLibraryLoader.get().requestDeletion(library);
            onRegistered();
        }
        catch (Exception e) {
            throw new ResourceLoaderException("Could not load local library due to: ", e);
        }
    }

    private File copyToTempDir(File file, File outputDir) throws IOException {
        File resourceCopiedToTempFolder = new File(outputDir, file.getName());
        Files.copy(file.toPath(), resourceCopiedToTempFolder.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES,
                NOFOLLOW_LINKS);
        return resourceCopiedToTempFolder;
    }
}
