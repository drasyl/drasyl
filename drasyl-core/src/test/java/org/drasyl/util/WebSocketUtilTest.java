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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.drasyl.util.WebSocketUtil.isWebSocketNonSecureURI;
import static org.drasyl.util.WebSocketUtil.isWebSocketSecureURI;
import static org.drasyl.util.WebSocketUtil.isWebSocketURI;
import static org.drasyl.util.WebSocketUtil.webSocketPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WebSocketUtilTest {
    @Nested
    class WebSocketPort {
        @Test
        void shouldReturnPortContainedInURI() {
            assertEquals(123, webSocketPort(URI.create("ws://localhost:123")));
        }

        @Test
        void shouldFallbackToDefaultWebsocketPort() {
            assertEquals(80, webSocketPort(URI.create("ws://localhost")));
        }

        @Test
        void shouldFallbackToDefaultSecureWebsocketPort() {
            assertEquals(443, webSocketPort(URI.create("wss://localhost")));
        }

        @SuppressWarnings({ "java:S5778" })
        @Test
        void shouldThrowExceptionForNonWebsocketURI() {
            assertThrows(IllegalArgumentException.class, () -> webSocketPort(URI.create("http://localhost")));
        }
    }

    @Nested
    class IsWebSocketSecureURI {
        @Test
        void shouldReturnTrueForWebSocketSecureURI() {
            assertTrue(isWebSocketSecureURI(URI.create("wss://localhost")));
        }

        @Test
        void shouldReturnFalseForWebSocketURI() {
            assertFalse(isWebSocketSecureURI(URI.create("ws://localhost")));
        }

        @Test
        void shouldReturnFalseForHttpURI() {
            assertFalse(isWebSocketSecureURI(URI.create("http://localhost")));
        }

        @Test
        void shouldReturnFalseForURIWithoutScheme() {
            assertFalse(isWebSocketSecureURI(URI.create("localhost")));
        }
    }

    @Nested
    class IsWebSocketNonSecureURI {
        @Test
        void shouldReturnTrueForWebSocketURI() {
            assertTrue(isWebSocketNonSecureURI(URI.create("ws://localhost")));
        }

        @Test
        void shouldReturnFalseForWebSocketSecureURI() {
            assertFalse(isWebSocketNonSecureURI(URI.create("wss://localhost")));
        }

        @Test
        void shouldReturnFalseForHttpURI() {
            assertFalse(isWebSocketNonSecureURI(URI.create("http://localhost")));
        }

        @Test
        void shouldReturnFalseForURIWithoutScheme() {
            assertFalse(isWebSocketNonSecureURI(URI.create("localhost")));
        }
    }

    @Nested
    class IsWebSocketURI {
        @Test
        void shouldReturnTrueForWebSocketURI() {
            assertTrue(isWebSocketURI(URI.create("ws://localhost")));
        }

        @Test
        void shouldReturnTrueForWebSocketSecureURI() {
            assertTrue(isWebSocketURI(URI.create("wss://localhost")));
        }

        @Test
        void shouldReturnFalseForHttpURI() {
            assertFalse(isWebSocketURI(URI.create("http://localhost")));
        }

        @Test
        void shouldReturnFalseForURIWithoutScheme() {
            assertFalse(isWebSocketURI(URI.create("localhost")));
        }
    }
}
