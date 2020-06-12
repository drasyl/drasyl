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

class PrivateIdentityTest {
    @Test
    void ofShouldNotThrowExceptionIfAddressCorrespondsToTheKey() throws CryptoException {
        assertNotNull(PrivateIdentity.of("c5461a6001", "030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3", "05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
    }

    @Test
    void ofShouldThrowExceptionIfAddressDoesNotCorrespondsToTheKey() {
        assertThrows(IllegalArgumentException.class, () -> PrivateIdentity.of("d40bee9aab", "030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3", "05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
    }
}