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

import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.util.DrasylSupplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityManagerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private DrasylSupplier<Identity, IdentityManagerException> identityGenerator;

    @Nested
    class LoadOrCreateIdentity {
        @Test
        void shouldLoadValidIdentityFromConfig() throws IdentityManagerException, CryptoException {
            when(config.getIdentityPublicKey()).thenReturn(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"));
            when(config.getIdentityProofOfWork()).thenReturn(ProofOfWork.of(15405649));
            when(config.getIdentityPrivateKey()).thenReturn(CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"));

            IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            identityManager.loadOrCreateIdentity();

            assertEquals(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), identityManager.getPublicKey());
        }

        @Test
        void shouldLoadIdentityIfConfigContainsNoKeysAndFileIsPresent(@TempDir Path dir) throws IOException, IdentityManagerException, CryptoException {
            Path path = Paths.get(dir.toString(), "my-identity.json");
            when(config.getIdentityPath()).thenReturn(path);

            // create existing file with identity
            Files.writeString(path, "{\n" +
                    "  \"proofOfWork\" : 15405649,\n" +
                    "  \"publicKey\" : \"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\n" +
                    "  \"privateKey\" : \"0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d\"\n" +
                    "}", StandardOpenOption.CREATE);

            IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            identityManager.loadOrCreateIdentity();

            assertEquals(
                    Identity.of(
                            ProofOfWork.of(15405649),
                            "0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9",
                            "0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"
                    ),
                    identityManager.getIdentity()
            );
        }

        @Test
        void shouldCreateNewIdentityIfConfigContainsNoKeysAndFileIsAbsent(@TempDir Path dir) throws IdentityManagerException {
            Path path = Paths.get(dir.toString(), "my-identity.json");
            when(config.getIdentityPath()).thenReturn(path);

            IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            identityManager.loadOrCreateIdentity();

            verify(identityGenerator).get();
            assertThat(path.toFile(), anExistingFile());
        }
    }

    @Nested
    class DeleteIdentityFile {
        @Test
        void shouldDeleteFile(@TempDir Path dir) throws IdentityManagerException, IOException {
            Path path = Paths.get(dir.toString(), "my-identity.json");
            path.toFile().createNewFile();

            IdentityManager.deleteIdentityFile(path);

            assertThat(path.toFile(), is(not(anExistingFile())));
        }
    }
}