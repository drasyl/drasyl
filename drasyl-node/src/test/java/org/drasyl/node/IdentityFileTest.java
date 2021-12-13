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
package org.drasyl.node;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static test.util.IdentityTestUtil.ID_1;

@ExtendWith(MockitoExtension.class)
class IdentityFileTest {
    @Nested
    class ReadFrom {
        @Test
        void shouldReadValidIdentityFromFile(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.ini");

            // create existing file with identity
            Files.writeString(path, "[Identity]\n" +
                    "SecretKey = " + ID_1.getIdentitySecretKey().toUnmaskedString() + "\n" +
                    "ProofOfWork = " + ID_1.getProofOfWork(), CREATE);

            assertEquals(ID_1, IdentityFile.readFrom(path));
        }

        @Test
        void shouldThrowExceptionForIncomplete(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.ini");

            // create existing file with identity
            Files.writeString(path, "[Identity]\n" +
                    "SecretKey = " + ID_1.getIdentitySecretKey().toUnmaskedString(), CREATE);

            assertThrows(IOException.class, () -> IdentityFile.readFrom(path));
        }

        @Test
        void shouldThrowExceptionForInvalidIdentity(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.ini");

            // create existing file with identity
            Files.writeString(path, "[Identity]\n" +
                    "SecretKey = 1234567890\n" +
                    "ProofOfWork = " + ID_1.getProofOfWork(), CREATE);

            assertThrows(IOException.class, () -> IdentityFile.readFrom(path));
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldWriteIdentityToFile(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "my-identity.ini");

            IdentityFile.writeTo(path, ID_1);

            assertEquals("[Identity]\n" +
                    "SecretKey = " + ID_1.getIdentitySecretKey().toUnmaskedString() + "\n" +
                    "ProofOfWork = " + ID_1.getProofOfWork(), Files.readString(path));
        }

        @Test
        void shouldThrowExceptionForInvalidPath(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "foo", "my-identity.ini");

            assertThrows(IOException.class, () -> IdentityFile.writeTo(path, ID_1));
        }
    }
}
