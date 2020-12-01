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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class EndpointTest {
    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentURI() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("udp://example.org", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("udp://example.com", CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentURI() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("udp://example.org", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() throws CryptoException {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint2 = Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));
            final Endpoint endpoint3 = Endpoint.of("udp://example.com", CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectStringForEndpointWithoutPublicKey() {
            assertEquals("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb", Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").toString());
        }

        @Test
        void shouldReturnCorrectStringForEndpointWithPublicKey() throws CryptoException {
            assertEquals("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb", Endpoint.of("udp://example.com", CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")).toString());
        }
    }

    @Nested
    class GetURI {
        @Test
        void shouldReturnCorrectURI() {
            assertEquals(
                    URI.create("udp://example.com"),
                    Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getURI()
            );
        }
    }

    @Nested
    class GetHost {
        @Test
        void shouldReturnHostOfURI() {
            assertEquals(
                    URI.create("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getHost(),
                    Endpoint.of("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getHost()
            );
        }
    }

    @Nested
    class GetPort {
        @Test
        void shouldReturnPortContainedInEndpoint() {
            assertEquals(123, Endpoint.of("udp://localhost:123#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getPort());
        }

        @Test
        void shouldFallbackToDefaultPort() {
            assertEquals(22527, Endpoint.of("udp://localhost#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getPort());
        }
    }

    @Nested
    class CompareTo {
        @Test
        void shouldReturnCorrectResult() {
            assertEquals(-1, Endpoint.of("udp://a#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").compareTo(Endpoint.of("udp://b#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55")));
            assertEquals(0, Endpoint.of("udp://a#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").compareTo(Endpoint.of("udp://a#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")));
            assertEquals(1, Endpoint.of("udp://b#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55").compareTo(Endpoint.of("udp://a#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")));
        }
    }

    @Nested
    class Of {
        @Test
        @SuppressWarnings("all")
        void shouldThrowNullPointerExceptionForNullValue() {
            assertThrows(NullPointerException.class, () -> Endpoint.of((URI) null));
            assertThrows(NullPointerException.class, () -> Endpoint.of((String) null));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForNonWebSocketURI() {
            final URI uri = URI.create("http://example.com#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of(uri));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidURI() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("\n"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionIfPublicKeyIsMissing() {
            final URI uri = URI.create("udp://example.com");
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of(uri));
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeEndpointToCorrectObject() throws IOException, CryptoException {
            final String json = "\"udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb\"";

            assertEquals(Endpoint.of(URI.create("udp://example.com"), CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")), JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectNonUdpEndpoint() {
            final String json = "\"http://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectEndpointWithoutPublicKey() {
            final String json = "\"udp://example.com\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeEndpointToCorrectJson() throws IOException, CryptoException {
            final Endpoint endpoint = Endpoint.of(URI.create("udp://example.com"), CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"));

            assertThatJson(JACKSON_WRITER.writeValueAsString(endpoint))
                    .isEqualTo("udp://example.com#030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        }
    }
}