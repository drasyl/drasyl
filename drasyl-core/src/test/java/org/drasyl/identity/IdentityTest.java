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

import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityTest {
    @Test
    void ofShouldNotThrowExceptionIfAddressCorrespondsToTheKey() throws CryptoException {
        assertNotNull(Identity.of("396dc9e224", "0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
    }

    @Test
    void ofShouldThrowExceptionIfAddressDoesNotCorrespondsToTheKey() {
        assertThrows(IllegalArgumentException.class, () -> Identity.of("d40bee9aab", "0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
    }

    @Test
    void equalsShouldReturnTrueOnSameAddress() throws CryptoException {
        Identity identity1 = Identity.of("396dc9e224");
        Identity identity2 = Identity.of("396dc9e224", "0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458");
        Identity identity3 = Identity.of("c5461a6001", "030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");

        assertEquals(identity1, identity2);
        assertEquals(identity2, identity1);
        assertNotEquals(identity1, identity3);
        assertNotEquals(identity3, identity1);
        assertNotEquals(identity2, identity3);
        assertNotEquals(identity3, identity2);
    }

    @Test
    void hashCodeShouldReturnTrueOnSameAddress() throws CryptoException {
        Identity identity1 = Identity.of("396dc9e224");
        Identity identity2 = Identity.of("396dc9e224", "0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458");
        Identity identity3 = Identity.of("c5461a6001", "030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");

        assertEquals(identity1.hashCode(), identity2.hashCode());
        assertEquals(identity2.hashCode(), identity1.hashCode());
        assertNotEquals(identity1.hashCode(), identity3.hashCode());
        assertNotEquals(identity3.hashCode(), identity1.hashCode());
        assertNotEquals(identity2.hashCode(), identity3.hashCode());
        assertNotEquals(identity3.hashCode(), identity2.hashCode());
    }
}