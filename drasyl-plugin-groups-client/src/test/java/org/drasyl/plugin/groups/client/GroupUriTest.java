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
package org.drasyl.plugin.groups.client;

import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupUriTest {
    private CompressedPublicKey manager;

    @BeforeEach
    void setUp() throws CryptoException {
        manager = CompressedPublicKey.of("03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b");
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskCredentials() {
            assertEquals(
                    "groups://***********@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/my-fancy-group?timeout=1",
                    GroupUri.of(manager, "credentials", "my-fancy-group", ofSeconds(1)).toString()
            );
        }
    }

    @Nested
    class Getters {
        @Test
        void shouldReturnManager() {
            final GroupUri options = GroupUri.of(manager, "credentials", "my-fancy-group", ofSeconds(1));

            assertEquals(manager, options.getManager());
        }

        @Test
        void shouldReturnSecret() {
            final GroupUri options = GroupUri.of(manager, "credentials", "my-fancy-group", ofSeconds(1));

            assertEquals("credentials", options.getCredentials());
        }

        @Test
        void shouldReturnName() {
            final GroupUri options = GroupUri.of(manager, "credentials", "my-fancy-group", ofSeconds(1));

            assertEquals("my-fancy-group", options.getName());
        }

        @Test
        void shouldReturnTimeout() {
            final GroupUri options = GroupUri.of(manager, "credentials", "my-fancy-group", ofSeconds(1));

            assertEquals(ofSeconds(1), options.getTimeout());
        }
    }

    @Nested
    class Creator {
        @Test
        void shouldCreateObjectOnCorrectURI() {
            assertEquals(
                    GroupUri.of(manager, "credentials", "my-fancy-group", ofMinutes(1)),
                    GroupUri.of("groups://credentials@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/my-fancy-group?timeout=60")
            );
        }

        @Test
        void shouldCreateObjectOnCorrectURIWithoutSecret() {
            assertEquals(
                    GroupUri.of(manager, "", "my-fancy-group", ofMinutes(1)),
                    GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/my-fancy-group?timeout=60")
            );
        }

        @Test
        void shouldThrowExceptionOnInvalidURI() {
            assertThrows(IllegalArgumentException.class, () ->
                    GroupUri.of("https://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60"));
            assertThrows(IllegalArgumentException.class, () ->
                    GroupUri.of("groups://abcd/group?timeout=beer"));
            assertThrows(IllegalArgumentException.class, () ->
                    GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b?timeout=60"));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60");

            assertEquals(options1, options2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group1?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group2?timeout=60");

            assertNotEquals(options1, options2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60");

            assertEquals(options1.hashCode(), options2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group1?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group2?timeout=60");

            assertNotEquals(options1.hashCode(), options2.hashCode());
        }
    }
}