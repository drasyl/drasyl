/*
 * Copyright (c) 2020-2021.
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
