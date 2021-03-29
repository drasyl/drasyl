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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.drasyl.util.UriUtil.createUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
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
    class GetQueryMap {
        @Test
        void shouldReturnQueryMap() {
            final URI uri = URI.create("schema://host/path?param1=a&param2=b");
            final Map<String, String> queryMap = UriUtil.getQueryMap(uri);

            assertThat(queryMap, hasEntry("param1", "a"));
            assertThat(queryMap, hasEntry("param2", "b"));
        }

        @Test
        void shouldReturnEmptyMapOnNullQuery() {
            final URI uri = URI.create("schema://host/path");
            final Map<String, String> queryMap = UriUtil.getQueryMap(uri);

            assertEquals(0, queryMap.size());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void shouldThrowExceptionOnNull() {
            assertThrows(NullPointerException.class, () -> UriUtil.getQueryMap(null));
        }
    }
}
