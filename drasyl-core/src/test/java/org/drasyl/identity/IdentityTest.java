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
}