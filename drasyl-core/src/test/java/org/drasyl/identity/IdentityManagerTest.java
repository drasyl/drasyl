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

import org.drasyl.DrasylConfig;
import org.drasyl.util.ThrowingSupplier;
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
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.drasyl.util.PathUtil.hasPosixSupport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityManagerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private ThrowingSupplier<Identity, IOException> identityGenerator;

    @Nested
    class LoadOrCreateIdentity {
        @Test
        void shouldLoadValidIdentityFromConfig() throws IOException {
            when(config.getIdentityPublicKey()).thenReturn(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"));
            when(config.getIdentityProofOfWork()).thenReturn(ProofOfWork.of(15405649));
            when(config.getIdentityPrivateKey()).thenReturn(CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"));

            final IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            identityManager.loadOrCreateIdentity();

            assertEquals(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), identityManager.getPublicKey());
        }

        @Test
        void shouldLoadIdentityIfConfigContainsNoKeysAndFileIsPresent(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");
            when(config.getIdentityPath()).thenReturn(path);

            // create existing file with identity
            Files.writeString(path, "{\n" +
                    "  \"proofOfWork\" : 15405649,\n" +
                    "  \"publicKey\" : \"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\n" +
                    "  \"privateKey\" : \"0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d\"\n" +
                    "}", StandardOpenOption.CREATE);
            if (hasPosixSupport(path)) {
                Files.setPosixFilePermissions(path, Set.of(OWNER_READ, OWNER_WRITE));
            }

            final IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
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
        void shouldCreateNewIdentityIfConfigContainsNoKeysAndFileIsAbsent(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");
            when(config.getIdentityPath()).thenReturn(path);
            when(identityGenerator.get()).thenReturn(Identity.of(
                    ProofOfWork.of(15405649),
                    "0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9",
                    "0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"
            ));

            final IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            identityManager.loadOrCreateIdentity();

            verify(identityGenerator).get();
            assertThat(path.toFile(), anExistingFile());
        }

        @Test
        void shouldThrowExceptionIfIdentityFromConfigIsInvalid() {
            when(config.getIdentityPublicKey()).thenReturn(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"));
            when(config.getIdentityProofOfWork()).thenReturn(ProofOfWork.of(42));
            when(config.getIdentityPrivateKey()).thenReturn(CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"));

            final IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            assertThrows(IOException.class, identityManager::loadOrCreateIdentity);
        }

        @Test
        void shouldThrowExceptionIfIdentityFromFileIsInvalid(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");
            when(config.getIdentityPath()).thenReturn(path);

            // create existing file with invalid identity
            Files.writeString(path, "{\n" +
                    "  \"proofOfWork\" : 42,\n" +
                    "  \"publicKey\" : \"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\n" +
                    "  \"privateKey\" : \"0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d\"\n" +
                    "}", StandardOpenOption.CREATE);

            final IdentityManager identityManager = new IdentityManager(identityGenerator, config, null);
            assertThrows(IOException.class, identityManager::loadOrCreateIdentity);
        }
    }

    @Nested
    class DeleteIdentityFile {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldDeleteFile(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");
            path.toFile().createNewFile();

            IdentityManager.deleteIdentityFile(path);

            assertThat(path.toFile(), is(not(anExistingFile())));
        }
    }
}
