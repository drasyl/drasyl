/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.node.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.DrasylException;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class provides the identity of the node. This is required for signing and encryption of
 * sent messages.
 */
public class IdentityManager {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Path path;
    private CompressedKeyPair keyPair;
    private Identity identity;

    /**
     * Manages the identity at the specified file path. If there is no identity at this file path
     * yet, a new one is created.
     *
     * @param path path to the identity
     */
    public IdentityManager(Path path) {
        this.path = path.toAbsolutePath();
    }

    IdentityManager(CompressedKeyPair keyPair, Identity identity, Path path) {
        this.keyPair = keyPair;
        this.identity = identity;
        this.path = path;
    }

    public void loadOrCreateIdentity() throws IdentityManagerException {
        if (isIdentityFilePresent(path)) {
            this.keyPair = readIdentityFile(path);
        }
        else {
            CompressedKeyPair keyPair = generateIdentity();
            writeIdentityFile(path, keyPair);
            this.keyPair = keyPair;
        }

        this.identity = Identity.of(keyPair.getPublicKey());
    }

    /**
     * Returns <code>true</code> if the identity file <code>path</code> exists. Otherwise
     * <code>false</code> is returned.
     *
     * @param path
     * @return
     */
    private static boolean isIdentityFilePresent(Path path) {
        return path.toFile().exists() && path.toFile().isFile();
    }

    /**
     * Reads the identity from <code>path</code>. Throws <code>IdentityManagerException</code> if
     * file cannot be read or file has unexpected content.
     *
     * @param path
     * @return
     * @throws IdentityManagerException
     */
    private static CompressedKeyPair readIdentityFile(Path path) throws IdentityManagerException {
        try {
            LOG.debug("Read identity from file '{}'", path);
            return OBJECT_MAPPER.readValue(path.toFile(), CompressedKeyPair.class);
        }
        catch (JsonProcessingException e) {
            throw new IdentityManagerException("Unable to load identity from file '" + path + "': " + e.getMessage());
        }
        catch (IOException e) {
            throw new IdentityManagerException("Unable to access identity file '" + path + "': " + e.getMessage());
        }
    }

    /**
     * Generates a new random identity.
     *
     * @return
     * @throws IdentityManagerException
     */
    private static CompressedKeyPair generateIdentity() throws IdentityManagerException {
        try {
            var newKeyPair = Crypto.generateKeys();
            return CompressedKeyPair.of(newKeyPair.getPublic(), newKeyPair.getPrivate());
        }
        catch (CryptoException e) {
            throw new IdentityManagerException("Unable to generate new identity: " + e.getMessage());
        }
    }

    /**
     * Writes the identity <code>keyPair</code> to the file <code>path</code>. Attention: If
     * <code>path</code> already contains an identity, it will be overwritten without warning.
     *
     * @param path
     * @param keyPair
     * @throws IdentityManagerException
     */
    private static void writeIdentityFile(Path path,
                                          CompressedKeyPair keyPair) throws IdentityManagerException {
        File file = path.toFile();

        if (Files.isDirectory(path) || !file.getParentFile().exists()) {
            throw new IdentityManagerException("Identity path '" + path + "' is a directory or path does not exist");
        }
        else if (file.exists() && !file.canWrite()) {
            throw new IdentityManagerException("Identity path '" + path + "' is not writable");
        }
        else {
            LOG.debug("Write identity to file '{}'", path);
            try {
                IdentityManager.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, keyPair);
            }
            catch (IOException e) {
                throw new IdentityManagerException("Unable to write identity to file '" + path + "': " + e.getMessage());
            }
        }
    }

    /**
     * @return returns the node identity.
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * @return returns the node key pair.
     */
    public CompressedKeyPair getKeyPair() {
        return keyPair;
    }
}
