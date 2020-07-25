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