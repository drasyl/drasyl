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

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class InetAddressWrapperTest {
    @Nested
    class Constructor {
        @Test
        void toStringShouldNotBeNull() {
            assertNotNull(InetAddressWrapper.of(InetAddress.getLoopbackAddress()).toString());
        }

        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> InetAddressWrapper.of(null), "InetAddress is required");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPublicKey() throws UnknownHostException {
            final InetAddressWrapper inetAddressWrapper1 = InetAddressWrapper.of(InetAddress.getLoopbackAddress());
            final InetAddressWrapper inetAddressWrapper2 = InetAddressWrapper.of(InetAddress.getLoopbackAddress());
            final InetAddressWrapper inetAddressWrapper3 = InetAddressWrapper.of(InetAddress.getLocalHost());

            assertEquals(inetAddressWrapper1, inetAddressWrapper2);
            assertNotEquals(inetAddressWrapper2, inetAddressWrapper3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPublicKey() throws UnknownHostException {
            final InetAddressWrapper inetAddressWrapper1 = InetAddressWrapper.of(InetAddress.getLoopbackAddress());
            final InetAddressWrapper inetAddressWrapper2 = InetAddressWrapper.of(InetAddress.getLoopbackAddress());
            final InetAddressWrapper inetAddressWrapper3 = InetAddressWrapper.of(InetAddress.getLocalHost());

            assertEquals(inetAddressWrapper1.hashCode(), inetAddressWrapper2.hashCode());
            assertNotEquals(inetAddressWrapper2.hashCode(), inetAddressWrapper3.hashCode());
        }
    }
}