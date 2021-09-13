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

import com.goterl.lazysodium.Sodium;
import com.goterl.lazysodium.utils.LibraryLoader;
import com.goterl.resourceloader.ResourceLoaderException;
import com.sun.jna.Native;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.goterl.lazysodium.utils.LibraryLoader.Mode.PREFER_SYSTEM;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class DrasylSodiumJava extends Sodium {
    public DrasylSodiumJava() {
        this(PREFER_SYSTEM);
    }

    public DrasylSodiumJava(LibraryLoader.Mode loadingMode) {
        new DrasylLibraryLoader(getClassesToRegister()).loadLibrary(loadingMode, "sodium");
        onRegistered();
    }

    public DrasylSodiumJava(File libFile) {
        try {
            File tempDir = DrasylResourceLoader.createMainTempDirectory();
            tempDir.mkdirs();

            // We want to copy this to a temp dir to have the right to change permissions
            File library = copyToTempDir(libFile, tempDir);
            DrasylSharedLibraryLoader.get().setPermissions(library);
            if (library.isDirectory()) {
                throw new ResourceLoaderException("Please supply a relative path to a file and not a directory.");
            }
            for (Class clzz : getClassesToRegister()) {
                Native.register(clzz, library.getAbsolutePath());
            }
            DrasylSharedLibraryLoader.get().requestDeletion(library);
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

    // Scrypt

    public native int crypto_pwhash_scryptsalsa208sha256(
            byte[] out,
            long outLen,
            byte[] password,
            long passwordLen,
            byte[] salt,
            long opsLimit,
            long memLimit
    );

    public native int crypto_pwhash_scryptsalsa208sha256_str(
            byte[] out,
            byte[] password,
            long passwordLen,
            long opsLimit,
            long memLimit
    );

    public native int crypto_pwhash_scryptsalsa208sha256_str_verify(
            byte[] str,
            byte[] password,
            long passwordLen
    );

    public native int crypto_pwhash_scryptsalsa208sha256_ll(
            byte[] password,
            int passwordLen,
            byte[] salt,
            int saltLen,
            long N,
            long r,
            long p,
            byte[] buf,
            int bufLen
    );

    public native int crypto_pwhash_scryptsalsa208sha256_str_needs_rehash(
            byte[] password,
            long opsLimit,
            long memLimit
    );

    // Salsa20 12 rounds

    public native void crypto_stream_salsa2012_keygen(byte[] key);

    public native int crypto_stream_salsa2012(
            byte[] c,
            long cLen,
            byte[] nonce,
            byte[] key
    );

    public native int crypto_stream_salsa2012_xor(
            byte[] cipher,
            byte[] message,
            long messageLen,
            byte[] nonce,
            byte[] key
    );

    public native void crypto_stream_salsa208_keygen(byte[] key);

    public native int crypto_stream_salsa208(
            byte[] c,
            long cLen,
            byte[] nonce,
            byte[] key
    );

    public native int crypto_stream_salsa208_xor(
            byte[] cipher,
            byte[] message,
            long messageLen,
            byte[] nonce,
            byte[] key
    );

    // XChaCha20

    public native int crypto_stream_xchacha20(
            byte[] c,
            long cLen,
            byte[] nonce,
            byte[] key
    );

    public native int crypto_stream_xchacha20_xor(
            byte[] cipher,
            byte[] message,
            long messageLen,
            byte[] nonce,
            byte[] key
    );

    public native int crypto_stream_xchacha20_xor_ic(
            byte[] cipher,
            byte[] message,
            long messageLen,
            byte[] nonce,
            long ic,
            byte[] key
    );

    public native void crypto_stream_xchacha20_keygen(byte[] key);

    public static List<Class> getClassesToRegister() {
        final List<Class> classes = new ArrayList<>();
        classes.add(Sodium.class);
        classes.add(DrasylSodiumJava.class);
        return classes;
    }
}
