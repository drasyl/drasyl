/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.models;

import org.drasyl.core.crypto.CompressedPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IdentityTest {
    private CompressedPublicKey compressedPublicKey;

    @BeforeEach
    void setUp() {
        compressedPublicKey = mock(CompressedPublicKey.class);
        when(compressedPublicKey.toString()).thenReturn("1234567890AAAABBBBCCCCDDDDEEEEFFFF");
    }

    @Test
    public void illegalIdentityShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            Identity.of("1234567890a");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Identity.of("123456789");
        });

        assertThrows(NullPointerException.class, () -> {
            Identity.of((String) null);
        });

        assertThrows(NullPointerException.class, () -> {
            Identity.of((CompressedPublicKey) null);
        });

        assertThrows(NullPointerException.class, () -> {
            Identity.verify(null, Identity.of("1234567890"));
        });

        assertThrows(NullPointerException.class, () -> {
            Identity.verify(compressedPublicKey, null);
        });
    }

    @Test
    public void aValidIdentityShouldBeCreatedFromACompressedPublicKey() {
        assertEquals(Identity.of("1234567890"), Identity.of(compressedPublicKey));
    }

    @Test
    public void sameIdShouldBeEquals() {
        Identity id1 = Identity.of("1234567890");
        Identity id2 = Identity.of("1234567890");
        ;

        assertEquals(id1, id2);
        assertEquals(id1, id1);
        assertEquals(id1.hashCode(), id2.hashCode());
        assertEquals(id1.getId(), id2.getId());
        assertNotEquals(Identity.of("0987654321"), id1);
        assertNotEquals(null, id1);
    }
}