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

import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.identity.IdentityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class IdentityManagerTest {

    @Test
    public void testKeyPairCreation(@TempDir Path tempDir) throws DrasylException {
        var keyPairFile = Paths.get(tempDir.toString(), "keyPair.json");
        IdentityManager identityManager1 = new IdentityManager(keyPairFile);
        CompressedKeyPair keyPair = identityManager1.getKeyPair();
        IdentityManager identityManager2 = new IdentityManager(keyPairFile);

        assertTrue(keyPairFile.toFile().exists());
        assertEquals(keyPair, identityManager2.getKeyPair());
        assertEquals(identityManager1.getIdentity(), identityManager2.getIdentity());
    }

    @Test
    public void shouldFailOnNonExistingPath(@TempDir Path tempDir) throws IOException, DrasylException {
        var invalidFile = Paths.get(tempDir.toString(), "keyPair.json").toFile();
        invalidFile.createNewFile();
        assertThrows(DrasylException.class, () -> new IdentityManager(Path.of("/to/something/that/doesnt/exist/file.json")));
        assertThrows(DrasylException.class, () -> new IdentityManager(invalidFile.toPath()));
    }

    @Test
    void renew(@TempDir Path tempDir) throws DrasylException {
        var keyPairFile = Paths.get(tempDir.toString(), "keyPair.json");
        IdentityManager identityManager = new IdentityManager(keyPairFile);
        CompressedKeyPair keyPair = identityManager.getKeyPair();
        identityManager.renew();

        assertNotEquals(keyPair, identityManager.getKeyPair());
    }
}