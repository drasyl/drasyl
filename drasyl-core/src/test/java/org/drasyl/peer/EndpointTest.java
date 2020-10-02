package org.drasyl.peer;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
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
        void notSameBecauseOfDifferentURI() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("ws://example.org", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("ws://example.com", CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentURI() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("ws://example.org", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("ws://example.com", CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectStringForEndpointWithoutPublicKey() {
            assertEquals("ws://example.com", Endpoint.of("ws://example.com").toString());
        }

        @Test
        void shouldReturnCorrectStringForEndpointWithPublicKey() throws CryptoException {
            assertEquals("ws://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb", Endpoint.of("ws://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")).toString());
        }
    }

    @Nested
    class GetURI {
        @Test
        void shouldReturnCorrectURI() {
            assertEquals(
                    URI.create("ws://example.com"),
                    Endpoint.of("ws://example.com").getURI()
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
            final URI uri = URI.create("http://example.com");
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
        void shouldDeserializeEndpointWithoutPublicKeyToCorrectObject() throws IOException {
            final String json = "\"ws://example.com\"";

            assertEquals(Endpoint.of(URI.create("ws://example.com")), JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldDeserializeEndpointWithPublicKeyToCorrectObject() throws IOException, CryptoException {
            final String json = "\"ws://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb\"";

            assertEquals(Endpoint.of(URI.create("ws://example.com"), CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")), JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectNonWebSocketEndpoint() {
            final String json = "\"http://example.com\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeEndpointWithoutPublicKeyToCorrectJson() throws IOException {
            final Endpoint endpoint = Endpoint.of(URI.create("wss://example.com"));

            assertThatJson(JACKSON_WRITER.writeValueAsString(endpoint))
                    .isEqualTo("wss://example.com");
        }

        @Test
        void shouldSerializeEndpointWithPublicKeyToCorrectJson() throws IOException, CryptoException {
            final Endpoint endpoint = Endpoint.of(URI.create("wss://example.com"), CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));

            assertThatJson(JACKSON_WRITER.writeValueAsString(endpoint))
                    .isEqualTo("wss://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        }
    }
}