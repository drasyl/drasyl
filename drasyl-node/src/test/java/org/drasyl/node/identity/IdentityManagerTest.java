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
package org.drasyl.node.identity;

import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
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
import static test.util.IdentityTestUtil.ID_1;

@ExtendWith(MockitoExtension.class)
class IdentityManagerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private ThrowingSupplier<Identity, IOException> identityGenerator;

    @Nested
    class ReadIdentityFile {
        @Test
        void shouldLoadIdentityIfConfigContainsNoKeysAndFileIsPresent(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");

            // create existing file with identity
            Files.writeString(path, "{\n" +
                    "  \"proofOfWork\" : " + ID_1.getProofOfWork().intValue() + ",\n" +
                    "  \"identityKeyPair\" : {" +
                    "  \"publicKey\" : \"" + ID_1.getIdentityPublicKey() + "\",\n" +
                    "  \"secretKey\" : \"" + ID_1.getIdentitySecretKey().toUnmaskedString() + "\"\n" +
                    "}," +
                    "  \"keyAgreementKeyPair\" : {" +
                    "  \"publicKey\" : \"" + ID_1.getKeyAgreementPublicKey() + "\",\n" +
                    "  \"secretKey\" : \"" + ID_1.getKeyAgreementSecretKey().toUnmaskedString() + "\"\n" +
                    "}" +
                    "}", StandardOpenOption.CREATE);
            if (hasPosixSupport(path)) {
                Files.setPosixFilePermissions(path, Set.of(OWNER_READ, OWNER_WRITE));
            }

            assertEquals(ID_1, IdentityManager.readIdentityFile(path));
        }

        @Test
        void shouldThrowExceptionIfIdentityFromFileIsInvalid(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");

            // create existing file with invalid identity
            Files.writeString(path, "{\n" +
                    "  \"proofOfWork\" : 42,\n" +
                    "  \"identityKeyPair\" : {" +
                    "  \"publicKey\" : \"" + ID_1.getIdentityPublicKey() + "\",\n" +
                    "  \"secretKey\" : \"" + ID_1.getIdentitySecretKey().toUnmaskedString() + "\"\n" +
                    "}," +
                    "  \"keyAgreementKeyPair\" : {" +
                    "  \"publicKey\" : \"" + ID_1.getKeyAgreementPublicKey() + "\",\n" +
                    "  \"secretKey\" : \"" + ID_1.getKeyAgreementSecretKey().toUnmaskedString() + "\"\n" +
                    "}" +
                    "}", StandardOpenOption.CREATE);

            assertThrows(IOException.class, () -> IdentityManager.readIdentityFile(path));
        }
    }

    @Nested
    class WriteIdentityFile {
        @Test
        void shouldCreateNewIdentityIfConfigContainsNoKeysAndFileIsAbsent(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.json");

            IdentityManager.writeIdentityFile(path, ID_1);

            assertThat(path.toFile(), anExistingFile());
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
