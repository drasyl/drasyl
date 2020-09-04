package org.drasyl.peer;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class EndpointTest {
    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferenURI() {
            Endpoint endpoint1 = Endpoint.of("ws://example.com");
            Endpoint endpoint2 = Endpoint.of("ws://example.com");
            Endpoint endpoint3 = Endpoint.of("ws://example.org");

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferenURI() {
            Endpoint endpoint1 = Endpoint.of("ws://example.com");
            Endpoint endpoint2 = Endpoint.of("ws://example.com");
            Endpoint endpoint3 = Endpoint.of("ws://example.org");

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            assertEquals("ws://example.com", Endpoint.of("ws://example.com").toString());
        }
    }

    @Nested
    class ToURI {
        @Test
        void shouldReturnCorrectURI() {
            assertEquals(
                    URI.create("ws://example.com"),
                    Endpoint.of("ws://example.com").toURI()
            );
        }
    }

    @Nested
    class GetHost {
        @Test
        void shouldReturnHostOfURI() {
            assertEquals(
                    URI.create("ws://example.com").getHost(),
                    Endpoint.of("ws://example.com").getHost()
            );
        }
    }

    @Nested
    class GetPort {
        @Test
        void shouldReturnPortContainedInEndpoint() {
            assertEquals(123, Endpoint.of("ws://localhost:123").getPort());
        }

        @Test
        void shouldFallbackToDefaultWebsocketPort() {
            assertEquals(80, Endpoint.of("ws://localhost").getPort());
        }

        @Test
        void shouldFallbackToDefaultSecureWebsocketPort() {
            assertEquals(443, Endpoint.of("wss://localhost").getPort());
        }
    }

    @Nested
    class IsSecureEndpoint {
        @Test
        void shouldReturnTrueForWebSocketSecureURI() {
            assertTrue(Endpoint.of("wss://localhost").isSecureEndpoint());
        }

        @Test
        void shouldReturnFalseForWebSocketURI() {
            assertFalse(Endpoint.of("ws://localhost").isSecureEndpoint());
        }
    }

    @Nested
    class CompareTo {
        @Test
        void shouldReturnCorrectResult() {
            assertEquals(-1, Endpoint.of("ws://a").compareTo(Endpoint.of("ws://b")));
            assertEquals(0, Endpoint.of("ws://a").compareTo(Endpoint.of("ws://a")));
            assertEquals(1, Endpoint.of("ws://b").compareTo(Endpoint.of("ws://a")));
        }
    }

    @Nested
    class Of {
        @Test
        void shouldThrowNullPointerExceptionForNullValue() {
            assertThrows(NullPointerException.class, () -> Endpoint.of((URI) null));
            assertThrows(NullPointerException.class, () -> Endpoint.of((String) null));
        }

        @Test
        void shouldThrowIllegalLinkExceptionForNonWebSocketURI() {
            URI uri = URI.create("http://example.com");
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of(uri));
        }

        @Test
        void shouldThrowIllegalLinkExceptionForInvalidURI() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("\n"));
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "\"ws://example.com\"";

            assertEquals(Endpoint.of(URI.create("ws://example.com")), JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectNonWebSocketEndpoint() {
            String json = "\"http://example.com\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            Endpoint endpoint = Endpoint.of(URI.create("wss://example.com"));

            assertThatJson(JACKSON_WRITER.writeValueAsString(endpoint))
                    .isEqualTo("wss://example.com");
        }
    }
}