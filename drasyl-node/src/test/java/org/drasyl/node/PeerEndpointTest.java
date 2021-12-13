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
package org.drasyl.node;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PeerEndpointTest {
    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentURI() {
            final PeerEndpoint endpoint1 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint2 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint3 = PeerEndpoint.of("udp://example.org:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final PeerEndpoint endpoint1 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint2 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint3 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_2.getIdentityPublicKey());

            assertEquals(endpoint1, endpoint2);
            assertNotEquals(endpoint2, endpoint3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentURI() {
            final PeerEndpoint endpoint1 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint2 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint3 = PeerEndpoint.of("udp://example.org:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }

        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final PeerEndpoint endpoint1 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint2 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());
            final PeerEndpoint endpoint3 = PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_2.getIdentityPublicKey());

            assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
            assertNotEquals(endpoint2.hashCode(), endpoint3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectStringForEndpointWithPublicKey() {
            assertEquals("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey(), PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()).toString());
        }
    }

    @Nested
    class GetURI {
        @Test
        void shouldReturnCorrectURI() {
            assertEquals(
                    URI.create("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()),
                    PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()).getURI()
            );
        }
    }

    @Nested
    class GetHost {
        @Test
        void shouldReturnHostOfURI() {
            assertEquals(
                    URI.create("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()).getHost(),
                    PeerEndpoint.of("udp://example.com:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()).getHost()
            );
        }
    }

    @Nested
    class GetPort {
        @Test
        void shouldReturnPortContainedInEndpoint() {
            assertEquals(123, PeerEndpoint.of("udp://localhost:123?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()).getPort());
        }
    }

    @Nested
    class ToInetSocketAddress {
        @Test
        void shouldReturnCorrectAddress() {
            assertEquals(new InetSocketAddress("localhost", 123), PeerEndpoint.of("udp://localhost:123?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey()).toInetSocketAddress());
        }
    }

    @SuppressWarnings("java:S5976")
    @Nested
    class Of {
        @Test
        void shouldThrowNullPointerExceptionForNullValue() {
            assertThrows(NullPointerException.class, () -> PeerEndpoint.of((URI) null));
            assertThrows(NullPointerException.class, () -> PeerEndpoint.of((String) null));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForNonWebSocketURI() {
            final String pk = IdentityTestUtil.ID_1.getIdentityPublicKey().toString();
            assertThrows(IllegalArgumentException.class, () -> PeerEndpoint.of("http://example.com?publicKey=" + pk));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForOldEndpointFormat() {
            final String pk = IdentityTestUtil.ID_1.getIdentityPublicKey().toString();
            assertThrows(IllegalArgumentException.class, () -> PeerEndpoint.of("udp://example.com:22527#" + pk));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidURI() {
            assertThrows(IllegalArgumentException.class, () -> PeerEndpoint.of("\n"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionIfPublicKeyIsMissing() {
            assertThrows(IllegalArgumentException.class, () -> PeerEndpoint.of("udp://example.com:22527"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionIfPortIsMissing() {
            final String pk = IdentityTestUtil.ID_1.getIdentityPublicKey().toString();
            assertThrows(IllegalArgumentException.class, () -> PeerEndpoint.of("udp://example.com?publicKey=" + pk));
        }

        @Test
        void shouldCreateCorrectEndpointWithoutNetworkIdFromString() {
            final PeerEndpoint endpoint = PeerEndpoint.of("udp://localhost:123?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey());

            assertEquals(PeerEndpoint.of("localhost", 123, IdentityTestUtil.ID_1.getIdentityPublicKey()), endpoint);
        }

        @Test
        void shouldCreateCorrectEndpointFromString() {
            final PeerEndpoint endpoint = PeerEndpoint.of("udp://localhost:123?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey() + "&networkId=1337");

            assertEquals(PeerEndpoint.of("localhost", 123, IdentityTestUtil.ID_1.getIdentityPublicKey(), 1337), endpoint);
        }
    }
}
