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
package org.drasyl.pipeline.address;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class InetSocketAddressWrapperTest {
    @Nested
    class Constructor {
        @Test
        void toStringShouldNotBeNull() {
            assertNotNull(InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 25527)).toString());
        }

        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> InetSocketAddressWrapper.of(null), "InetSocketAddress is required");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final InetSocketAddressWrapper addressWrapper1 = InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 25527));
            final InetSocketAddressWrapper addressWrapper2 = InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 25527));
            final InetSocketAddressWrapper addressWrapper3 = InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 8080));

            assertEquals(addressWrapper1, addressWrapper2);
            assertNotEquals(addressWrapper2, addressWrapper3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final InetSocketAddressWrapper addressWrapper1 = InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 25527));
            final InetSocketAddressWrapper addressWrapper2 = InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 25527));
            final InetSocketAddressWrapper addressWrapper3 = InetSocketAddressWrapper.of(InetSocketAddress.createUnresolved("127.0.0.1", 8080));

            assertEquals(addressWrapper1.hashCode(), addressWrapper2.hashCode());
            assertNotEquals(addressWrapper2.hashCode(), addressWrapper3.hashCode());
        }
    }
}