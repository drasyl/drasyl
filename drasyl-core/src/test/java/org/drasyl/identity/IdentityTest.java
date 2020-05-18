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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IdentityTest {
    private CompressedPublicKey compressedPublicKey;
    private Identity identity;

    @BeforeEach
    void setUp() {
        identity = Identity.of("41eaac1ee8");
        compressedPublicKey = mock(CompressedPublicKey.class);
        when(compressedPublicKey.toString()).thenReturn("ead3151c64AAAABBBBCCCCDDDDEEEEFFFF");
    }

    @Test
    void toStringCase() {
        assertEquals("Identity{id=41eaac1ee8}", identity.toString());
    }

    @Test
    void illegalIdentityShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> Identity.of("1234567890a"));
        assertThrows(IllegalArgumentException.class, () -> Identity.of("123456789"));
        assertThrows(NullPointerException.class, () -> Identity.of((String) null));
        assertThrows(NullPointerException.class, () -> Identity.of((CompressedPublicKey) null));
        assertThrows(NullPointerException.class, () -> Identity.verify(null, identity));
        assertThrows(NullPointerException.class, () -> Identity.verify(compressedPublicKey, null));
    }

    @Test
    void aValidIdentityShouldBeCreatedFromACompressedPublicKey() {
        assertEquals(identity, Identity.of(compressedPublicKey));
    }

    @Test
    void sameIdShouldBeEquals() {
        Identity id2 = Identity.of("41eaac1ee8");

        assertEquals(identity, id2);
        assertEquals(identity, identity);
        assertEquals(identity.hashCode(), id2.hashCode());
        assertEquals(identity.getId(), id2.getId());
        assertNotEquals(Identity.of("0987654321"), identity);
        assertNotEquals(null, identity);
    }
}