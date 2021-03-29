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
package org.drasyl.peer;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
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
        void notSameBecauseOfDifferentURI() {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint2 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint3 = Endpoint.of("udp://example.org:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint2 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint3 = Endpoint.of("udp://example.com:22527?publicKey=033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentURI() {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint2 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint3 = Endpoint.of("udp://example.org:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final Endpoint endpoint1 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint2 = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            final Endpoint endpoint3 = Endpoint.of("udp://example.com:22527?publicKey=033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectStringForEndpointWithPublicKey() {
            assertEquals("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb", Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").toString());
        }
    }

    @Nested
    class GetURI {
        @Test
        void shouldReturnCorrectURI() {
            assertEquals(
                    URI.create("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"),
                    Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getURI()
            );
        }
    }

    @Nested
    class GetHost {
        @Test
        void shouldReturnHostOfURI() {
            assertEquals(
                    URI.create("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getHost(),
                    Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getHost()
            );
        }
    }

    @Nested
    class GetPort {
        @Test
        void shouldReturnPortContainedInEndpoint() {
            assertEquals(123, Endpoint.of("udp://localhost:123?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").getPort());
        }
    }

    @Nested
    class ToInetSocketAddress {
        @Test
        void shouldReturnCorrectAddress() {
            assertEquals(new InetSocketAddressWrapper("localhost", 123), Endpoint.of("udp://localhost:123?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").toInetSocketAddress());
        }
    }

    @SuppressWarnings("java:S5976")
    @Nested
    class Of {
        @Test
        void shouldThrowNullPointerExceptionForNullValue() {
            assertThrows(NullPointerException.class, () -> Endpoint.of((URI) null));
            assertThrows(NullPointerException.class, () -> Endpoint.of((String) null));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForNonWebSocketURI() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("http://example.com?publicKey=033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForOldEndpointFormat() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("udp://example.com:22527#033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidURI() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("\n"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionIfPublicKeyIsMissing() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("udp://example.com:22527"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionIfPortIsMissing() {
            assertThrows(IllegalArgumentException.class, () -> Endpoint.of("udp://example.com?publicKey=033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"));
        }

        @Test
        void shouldCreateCorrectEndpointWithoutNetworkIdFromString() {
            final Endpoint endpoint = Endpoint.of("udp://localhost:123?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");

            assertEquals(Endpoint.of("localhost", 123, CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb")), endpoint);
        }

        @Test
        void shouldCreateCorrectEndpointFromString() {
            final Endpoint endpoint = Endpoint.of("udp://localhost:123?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb&networkId=1337");

            assertEquals(Endpoint.of("localhost", 123, CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"), 1337), endpoint);
        }
    }

    @SuppressWarnings("java:S5976")
    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeEndpointToCorrectObject() throws IOException {
            final String json = "\"udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb\"";

            assertEquals(Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"), JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectNonUdpEndpoint() {
            final String json = "\"http://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectEndpointWithoutPublicKey() {
            final String json = "\"udp://example.com:22527\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }

        @Test
        void shouldRejectEndpointWithoutPort() {
            final String json = "\"udp://example.com?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb\"";

            assertThrows(JsonMappingException.class, () -> JACKSON_READER.readValue(json, Endpoint.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeEndpointToCorrectJson() throws IOException {
            final Endpoint endpoint = Endpoint.of("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");

            assertThatJson(JACKSON_WRITER.writeValueAsString(endpoint))
                    .isEqualTo("udp://example.com:22527?publicKey=030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        }
    }
}
