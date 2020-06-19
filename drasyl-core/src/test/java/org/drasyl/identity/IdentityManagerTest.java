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
package org.drasyl.identity;

import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityManagerTest {
    private DrasylNodeConfig config;

    @BeforeEach
    void setUp() {
        config = mock(DrasylNodeConfig.class);
    }

    @Test
    void loadOrCreateIdentityShouldLoadValidIdentityFromConfig() throws IdentityManagerException, CryptoException {
        when(config.getIdentityPublicKey()).thenReturn(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"));
        when(config.getProofOfWork()).thenReturn(ProofOfWork.of(15405649));
        when(config.getIdentityPrivateKey()).thenReturn(CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"));

        IdentityManager identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        assertEquals(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), identityManager.getPublicKey());
    }

    @Test
    void loadOrCreateIdentityShouldLoadIdentityIfConfigContainsNoKeysAndFileIsPresent(@TempDir Path dir) throws IOException, IdentityManagerException, CryptoException {
        Path path = Paths.get(dir.toString(), "my-identity.json");
        when(config.getIdentityPath()).thenReturn(path);

        // create existing file with identity
        Files.writeString(path, "{\n" +
                "  \"proofOfWork\" : 15405649,\n" +
                "  \"publicKey\" : \"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\n" +
                "  \"privateKey\" : \"0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d\"\n" +
                "}", StandardOpenOption.CREATE);

        IdentityManager identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        assertEquals(
                Identity.of(ProofOfWork.of(15405649),
                        "0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9",
                        "0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"
                ),
                identityManager.getIdentity()
        );
    }

    @Test
    void loadOrCreateIdentityShouldThrowExceptionIfConfigContainsNoKeysAndPathDoesNotExist(@TempDir Path dir) {
        Path path = Paths.get(dir.toString(), "non-existing", "my-identity.json");
        when(config.getIdentityPath()).thenReturn(path);

        IdentityManager identityManager = new IdentityManager(config);

        assertThrows(IdentityManagerException.class, identityManager::loadOrCreateIdentity);
    }

    @Test
    void loadOrCreateIdentityShouldCreateNewIdentityIfConfigContainsNoKeysAndFileIsAbsent(@TempDir Path dir) throws IdentityManagerException, IOException {
        Path path = Paths.get(dir.toString(), "my-identity.json");
        when(config.getIdentityPath()).thenReturn(path);

        IdentityManager identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        assertNotNull(identityManager.getProofOfWork());
        assertNotNull(identityManager.getPublicKey());
        assertNotNull(identityManager.getPrivateKey());

        assertThatJson(Files.readString(path))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\n" +
                        "  \"proofOfWork\" : " + identityManager.getProofOfWork().getNonce() + ",\n" +
                        "  \"publicKey\" : \"" + identityManager.getPublicKey() + "\",\n" +
                        "  \"privateKey\" : \"" + identityManager.getPrivateKey() + "\"\n" +
                        "}");
    }
}