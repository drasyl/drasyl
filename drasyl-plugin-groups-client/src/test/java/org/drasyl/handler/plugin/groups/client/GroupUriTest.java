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
package org.drasyl.handler.plugin.groups.client;

import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupUriTest {
    private IdentityPublicKey manager;

    @BeforeEach
    void setUp() {
        manager = IdentityTestUtil.ID_1.getIdentityPublicKey();
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskCredentials() {
            assertEquals(
                    "groups://***********@" + manager + "/my-fancy-group?timeout=1",
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
                    GroupUri.of("groups://credentials@" + manager + "/my-fancy-group?timeout=60")
            );
        }

        @Test
        void shouldCreateObjectOnCorrectURIWithoutSecret() {
            assertEquals(
                    GroupUri.of(manager, "", "my-fancy-group", ofMinutes(1)),
                    GroupUri.of("groups://" + manager + "/my-fancy-group?timeout=60")
            );
        }

        @Test
        void shouldThrowExceptionOnInvalidURI() {
            assertThrows(IllegalArgumentException.class, () ->
                    GroupUri.of("https://secret@" + manager + "/group?timeout=60"));
            assertThrows(IllegalArgumentException.class, () ->
                    GroupUri.of("groups://abcd/group?timeout=beer"));
            assertThrows(IllegalArgumentException.class, () ->
                    GroupUri.of("groups://" + manager + "?timeout=60"));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://" + manager + "/group?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://" + manager + "/group?timeout=60");

            assertEquals(options1, options2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://" + manager + "/group1?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://" + manager + "/group2?timeout=60");

            assertNotEquals(options1, options2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://" + manager + "/group?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://" + manager + "/group?timeout=60");

            assertEquals(options1.hashCode(), options2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupUri options1 = GroupUri.of("groups://" + manager + "/group1?timeout=60");
            final GroupUri options2 = GroupUri.of("groups://" + manager + "/group2?timeout=60");

            assertNotEquals(options1.hashCode(), options2.hashCode());
        }
    }
}
