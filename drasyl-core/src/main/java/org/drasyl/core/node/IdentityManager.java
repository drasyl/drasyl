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
package org.drasyl.core.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drasyl.core.models.*;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class manages the identity/KeyPair of a node.
 */
public class IdentityManager {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityManager.class);
    private static ObjectMapper objectMapper = new ObjectMapper();
    private CompressedKeyPair keyPair;
    private Identity identity;
    private final Path path;

    /**
     * Manages the identity at the specified file path. If there is no identity at this file path
     * yet, a new one is created.
     *
     * @param path path to the identity
     */
    public IdentityManager(Path path) throws DrasylException {
        this.path = path.toAbsolutePath();

        if (path.toFile().exists() && path.toFile().isFile()) {
            load();
        }
        else {
            renew();
        }
    }

    IdentityManager(CompressedKeyPair keyPair, Identity identity, Path path) {
        this.keyPair = keyPair;
        this.identity = identity;
        this.path = path;
    }

    /**
     * This function irrevocably replaces the old identity with a new one. Required if the super
     * peer reports {@link Code#NODE_IDENTITY_COLLISION}.
     */
    public void renew() throws DrasylException {
        File file = path.toFile();

        if (Files.isDirectory(path) || !file.getParentFile().exists()) {
            throw new DrasylException("Identity path (" + path.toString() + ") points not to a file.");
        }
        else if (file.exists() && !file.canRead()) {
            throw new DrasylException("No read permission on identity file.");
        }
        else if (file.exists() && !file.canWrite()) {
            throw new DrasylException("No write permission on identity file.");
        }
        else {
            LOG.debug("No identity present. Generate a new one at file {}", path);
            generate();
            try {
                IdentityManager.objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, keyPair);
            }
            catch (IOException e) {
                throw new DrasylException("Can't save identity file. "+e.getMessage());
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

    private void load() throws DrasylException {
        try {
        LOG.debug("Load existing identity from file {}", path);
            this.keyPair = IdentityManager.objectMapper.readValue(path.toFile(), CompressedKeyPair.class);
            this.identity = Identity.of(keyPair.getPublicKey());
        }
        catch (IOException e) {
            throw new DrasylException("Can't load identity (" + path.toAbsolutePath() + "). " + e.getMessage());
        }
    }

    private void generate() {
        try {
            var newKeyPair = Crypto.generateKeys();
            this.keyPair = CompressedKeyPair.of(newKeyPair.getPublic(), newKeyPair.getPrivate());
            this.identity = Identity.of(keyPair.getPublicKey());
        }
        catch (CryptoException e) {
            LOG.error("Fatal Error on identity creation.", e);
        }
    }
}
