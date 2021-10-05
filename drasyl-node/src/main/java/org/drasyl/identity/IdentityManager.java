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
package org.drasyl.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.util.ThrowingSupplier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.PathUtil.hasPosixSupport;

/**
 * This class holds the identity of the node. Messages to the node are addressed to the identity's
 * public key. Messages will be encrypted with public-private key pair contained in the identity.
 */
public class IdentityManager {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityManager.class);
    private final ThrowingSupplier<Identity, IOException> identityGenerator;
    private final DrasylConfig config;
    private Identity identity;

    /**
     * Manages the identity at the specified file path. If there is no identity at this file path
     * yet, a new one is created.
     */
    public IdentityManager(final DrasylConfig config) {
        this(IdentityManager::generateIdentity, config, null);
    }

    @SuppressWarnings("SameParameterValue")
    IdentityManager(final ThrowingSupplier<Identity, IOException> identityGenerator,
                    final DrasylConfig config,
                    final Identity identity) {
        this.identityGenerator = identityGenerator;
        this.config = config;
        this.identity = identity;
    }

    /**
     * Attempts to load the identity defined in the configuration: First it tries to read the key
     * pair directly from the configuration. If no key pair is specified there, the identity is
     * loaded from the identity file path specified in the configuration. If the file does not
     * exist, a new identity is generated and written to the file. If all this fails and no identity
     * can be loaded, an {@link IOException} is thrown.
     *
     * @throws IOException if identity could not be loaded or created
     */
    public void loadOrCreateIdentity() throws IOException {
        if (config.getIdentityProofOfWork() != null && config.getIdentityPublicKey() != null && config.getIdentitySecretKey() != null) {
            LOG.info("Load identity specified in config");
            try {
                this.identity = Identity.of(config.getIdentityProofOfWork(), config.getIdentityPublicKey(), config.getIdentitySecretKey());
            }
            catch (final IllegalArgumentException e) {
                throw new IOException("Identity read from configuration seems invalid", e);
            }
        }
        else {
            final Path path = config.getIdentityPath();

            if (isIdentityFilePresent(path)) {
                LOG.info("Read Identity from file `{}`", path);
                this.identity = readIdentityFile(path);
            }
            else {
                LOG.info("No Identity present. Generate a new one and write to file `{}`.", path);
                final Identity myIdentity = identityGenerator.get();
                writeIdentityFile(path, myIdentity);
                this.identity = myIdentity;
            }
        }

        if (!this.identity.isValid()) {
            throw new IOException("Loaded or Created Identity is invalid.");
        }
    }

    /**
     * Returns <code>true</code> if the identity file <code>path</code> exists. Otherwise
     * <code>false</code> is returned.
     *
     * @param path path to identity file
     * @return {@code true} if file exists
     */
    private static boolean isIdentityFilePresent(final Path path) {
        return path.toFile().exists() && path.toFile().isFile();
    }

    /**
     * Reads the identity from {@code code}. Throws {@code IOException} if file cannot be read or
     * file has unexpected content.
     *
     * @param path path to identity file
     * @return The identity contained in the file
     * @throws IOException if identity could not be read from file
     */
    private static Identity readIdentityFile(final Path path) throws IOException {
        try {
            // check if file permissions are too open
            if (hasPosixSupport(path)) {
                final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
                if (!Collections.disjoint(permissions, Set.of(GROUP_EXECUTE, GROUP_WRITE, GROUP_READ, OTHERS_EXECUTE, OTHERS_WRITE, OTHERS_READ))) {
                    throw new IOException("Unprotected private key: It is required that your identity file '" + path + "' is NOT accessible by others.'");
                }
            }

            return JACKSON_READER.readValue(path.toFile(), Identity.class);
        }
        catch (final JsonProcessingException e) {
            throw new IOException("Unable to load identity from file '" + path + "': ", e);
        }
    }

    /**
     * Generates a new random identity.
     *
     * @return the generated identity
     * @throws IOException if an identity could not be generated
     */
    public static Identity generateIdentity() throws IOException {
        try {
            final KeyPair<IdentityPublicKey, IdentitySecretKey> identityKeyPair = Crypto.INSTANCE.generateLongTimeKeyPair();
            final ProofOfWork pow = ProofOfWork.generateProofOfWork(identityKeyPair.getPublicKey(), Identity.POW_DIFFICULTY);

            return Identity.of(pow, identityKeyPair);
        }
        catch (final CryptoException e) {
            throw new IOException("Unable to generate new identity", e);
        }
    }

    /**
     * Writes the identity {@code identity} to the file {@code path}. Attention: If {@code path}
     * already contains an identity, it will be overwritten without warning.
     *
     * @param path     path where the identity should be written to
     * @param identity this identity is written to the file
     * @throws IOException if the identity could not be written to the file
     */
    private static void writeIdentityFile(final Path path,
                                          final Identity identity) throws IOException {
        final File file = path.toFile();

        if (Files.isDirectory(path) || (file.getParentFile() != null && !file.getParentFile().exists())) {
            throw new IOException("Identity path '" + path + "' is a directory or path does not exist");
        }
        else if (file.exists() && !file.canWrite()) {
            throw new IOException("Identity path '" + path + "' is not writable");
        }
        else {
            // ensure that file permissions will not be too open
            if (hasPosixSupport(path) && file.createNewFile()) {
                Files.setPosixFilePermissions(path, Set.of(OWNER_READ, OWNER_WRITE));
            }

            JACKSON_WRITER.with(new DefaultPrettyPrinter()).writeValue(file, identity);
        }
    }

    public IdentityPublicKey getIdentityPublicKey() {
        return identity.getIdentityPublicKey();
    }

    public IdentitySecretKey getIdentityPrivateKey() {
        return identity.getIdentitySecretKey();
    }

    public ProofOfWork getProofOfWork() {
        return identity.getProofOfWork();
    }

    /**
     * @return returns the node identity.
     */
    public Identity getIdentity() {
        return requireNonNull(identity);
    }

    /**
     * Deletes the identity file specified in the configuration.
     * <p>
     * ATTENTION: Messages directed to the present identity can then no longer be decrypted and
     * read. This step is irreversible. Should only be used if the present identity should never be
     * used again!
     *
     * @throws IOException if identity file could not be deleted
     */
    public static void deleteIdentityFile(final Path path) throws IOException {
        final File file = path.toFile();

        if (!file.exists()) {
            // nothing to do
            return;
        }

        Files.delete(path);
    }
}
