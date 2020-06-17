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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void ofShouldNotThrowExceptionIfAddressCorrespondsToTheKey() {
        assertNotNull(Identity.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
    }

    @Test
    void ofShouldThrowExceptionIfAddressDoesNotCorrespondsToTheKey() {
        assertThrows(IllegalArgumentException.class, () -> Identity.of("e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
    }

    @Test
    @Disabled("Not implemented yet")
    void fromJsonShouldThrowExceptionIfAddressDoesNotCorrespondsToTheKey() {
        String json = "{\n" +
                "  \"publicKey\": \"0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458\"\n" +
                "}";

        assertThrows(IllegalArgumentException.class, () -> JSON_MAPPER.readValue(json, Identity.class));
    }

    @Test
    void equalsShouldReturnTrueOnSameAddress() {
        Identity identity1 = Identity.of("026abb56739382769fc906849c34b24cd403dd250607f3133803f459eac04e608d");
        Identity identity2 = Identity.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        Identity identity3 = Identity.of("02264a4f8d81c0a271708ae5db3bf14d24262179d7b0595ba4ee90d435d4dc1170");
        Identity identity4 = Identity.of("026abb56739382769fc906849c34b24cd403dd250607f3133803f459eac04e608d");

        assertNotEquals(identity1, identity3);
        assertNotEquals(identity3, identity1);
        assertNotEquals(identity2, identity3);
        assertNotEquals(identity3, identity2);
        assertNotEquals(identity1, identity2);
        assertNotEquals(identity2, identity1);
        assertEquals(identity1, identity4);
        assertEquals(identity4, identity1);
    }

    @Test
    void hashCodeShouldReturnTrueOnSameAddress() {
        Identity identity1 = Identity.of("026abb56739382769fc906849c34b24cd403dd250607f3133803f459eac04e608d");
        Identity identity2 = Identity.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        Identity identity3 = Identity.of("02264a4f8d81c0a271708ae5db3bf14d24262179d7b0595ba4ee90d435d4dc1170");
        Identity identity4 = Identity.of("026abb56739382769fc906849c34b24cd403dd250607f3133803f459eac04e608d");

        assertNotEquals(identity1.hashCode(), identity3.hashCode());
        assertNotEquals(identity3.hashCode(), identity1.hashCode());
        assertNotEquals(identity2.hashCode(), identity3.hashCode());
        assertNotEquals(identity3.hashCode(), identity2.hashCode());
        assertNotEquals(identity1.hashCode(), identity2.hashCode());
        assertNotEquals(identity2.hashCode(), identity1.hashCode());
        assertEquals(identity1.hashCode(), identity4.hashCode());
        assertEquals(identity4.hashCode(), identity1.hashCode());
    }
}