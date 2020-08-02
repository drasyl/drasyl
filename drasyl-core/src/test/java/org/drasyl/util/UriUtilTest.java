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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.drasyl.util.UriUtil.createUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriUtilTest {
    @Nested
    class CreateUri {
        @Test
        void shouldReturnUriForValidComponentsForValidComponents() {
            assertEquals(URI.create("ws://example.com:22527"), createUri("ws", "example.com", 22527));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidComponents() {
            assertThrows(IllegalArgumentException.class, () -> createUri("ws", "\n", 22527));
        }
    }

    @Nested
    class OverridePort {
        @Test
        void shouldReturnCorrectValue() {
            assertEquals(URI.create("ws://example.com:22527"), UriUtil.overridePort(URI.create("ws://example.com"), 22527));
            assertEquals(URI.create("ws://example.com:22527/foo?bar=baz#hello-world"), UriUtil.overridePort(URI.create("ws://example.com:1337/foo?bar=baz#hello-world"), 22527));
        }
    }
}