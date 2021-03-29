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
package org.drasyl.event;

import org.drasyl.identity.Identity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class NodeTest {
    @Nested
    class Of {
        @Test
        void shouldAcceptValidInputs(@Mock final Identity identity) {
            assertNotNull(Node.of(identity));
            assertNotNull(Node.of(identity, 1));
            assertNotNull(Node.of(identity, 1, 2));
        }

        @Test
        void shouldRejectNegativePort(@Mock final Identity identity) {
            assertThrows(IllegalArgumentException.class, () -> Node.of(identity, -1));
            assertThrows(IllegalArgumentException.class, () -> Node.of(identity, 1, -1));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals(@Mock final Identity identity) {
            final Node node1 = Node.of(identity, 1, 2);
            final Node node2 = Node.of(identity, 1, 2);

            assertEquals(node1, node1);
            assertEquals(node1, node2);
        }

        @Test
        void shouldNotBeEquals(@Mock final Identity identity) {
            final Node node1 = Node.of(identity, 1, 2);
            final Node node2 = Node.of(identity, 2, 2);

            assertNotEquals(node1, node2);
            assertNotEquals(node1, new Object());
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals(@Mock final Identity identity) {
            final Node node1 = Node.of(identity, 1, 2);
            final Node node2 = Node.of(identity, 1, 2);

            assertEquals(node1.hashCode(), node2.hashCode());
        }

        @Test
        void shouldNotBeEquals(@Mock final Identity identity) {
            final Node node1 = Node.of(identity, 1, 2);
            final Node node2 = Node.of(identity, 2, 2);

            assertNotEquals(node1.hashCode(), node2.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnString(@Mock final Identity identity) {
            assertNotNull(Node.of(identity, 1).toString());
        }
    }

    @Nested
    class Getters {
        @Test
        void shouldReturnCorrectValues(@Mock final Identity identity) {
            final Node node = Node.of(identity, 1, 2);

            assertEquals(identity, node.getIdentity());
            assertEquals(1, node.getPort());
            assertEquals(2, node.getTcpFallbackPort());
        }
    }
}
