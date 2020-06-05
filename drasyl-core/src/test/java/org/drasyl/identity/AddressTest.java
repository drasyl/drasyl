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
package org.drasyl.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddressTest {
    private CompressedPublicKey compressedPublicKey;
    private Address address;

    @BeforeEach
    void setUp() {
        address = Address.of("41eaac1ee8");
        compressedPublicKey = mock(CompressedPublicKey.class);
        when(compressedPublicKey.toString()).thenReturn("ead3151c64AAAABBBBCCCCDDDDEEEEFFFF");
    }

    @Test
    void testToString() {
        assertEquals("41eaac1ee8", address.toString());
    }

    @Test
    void illegalIdentityShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> Address.of("1234567890a"));
        assertThrows(IllegalArgumentException.class, () -> Address.of("123456789"));
        assertThrows(NullPointerException.class, () -> Address.of((String) null));
        assertThrows(NullPointerException.class, () -> Address.of((CompressedPublicKey) null));
        assertThrows(NullPointerException.class, () -> Address.verify(null, address));
        assertThrows(NullPointerException.class, () -> Address.verify(compressedPublicKey, null));
    }

    @Test
    void aValidIdentityShouldBeCreatedFromACompressedPublicKey() {
        assertEquals(address, Address.of(compressedPublicKey));
    }

    @Test
    void sameIdShouldBeEquals() {
        Address id2 = Address.of("41eaac1ee8");

        assertEquals(address, id2);
        assertEquals(address, address);
        assertEquals(address.hashCode(), id2.hashCode());
        assertEquals(address.getId(), id2.getId());
        assertNotEquals(Address.of("0987654321"), address);
        assertNotEquals(null, address);
    }
}