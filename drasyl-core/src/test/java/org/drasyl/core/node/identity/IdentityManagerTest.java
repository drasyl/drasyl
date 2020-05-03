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

import com.google.common.io.Files;
import org.drasyl.core.models.DrasylException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class IdentityManagerTest {
    @Test
    public void loadOrCreateIdentityShouldLoadIdentityIfFileIsPresent(@TempDir Path dir) throws IOException, IdentityManagerException {
        // create existing file with identity
        Path path = Paths.get(dir.toString(), "my-identity.json");
        Files.write("{\n" +
                "  \"publicKey\" : \"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\n" +
                "  \"privateKey\" : \"0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d\"\n" +
                "}", path.toFile(), Charset.defaultCharset());

        IdentityManager identityManager = new IdentityManager(path);
        identityManager.loadOrCreateIdentity();

        assertEquals(Identity.of("0229041b27"), identityManager.getIdentity());
    }

    @Test
    public void loadOrCreateIdentityShouldThrowExceptionIfPathDoesNotExist(@TempDir Path dir) {
        Path path = Paths.get(dir.toString(), "non-existing", "my-identity.json");

        IdentityManager identityManager = new IdentityManager(path);

        assertThrows(IdentityManagerException.class, () -> identityManager.loadOrCreateIdentity());
    }

    @Test
    public void loadOrCreateIdentityShouldCreateNewIdentityIfFileIsAbsent(@TempDir Path dir) throws IdentityManagerException {
        Path path = Paths.get(dir.toString(), "my-identity.json");

        IdentityManager identityManager = new IdentityManager(path);
        identityManager.loadOrCreateIdentity();

        assertNotNull(identityManager.getIdentity());
    }
}