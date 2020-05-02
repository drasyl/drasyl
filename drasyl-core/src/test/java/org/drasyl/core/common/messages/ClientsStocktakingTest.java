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
package org.drasyl.core.common.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ClientsStocktakingTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private final Identity identity = IdentityTestHelper.random();

    @Test
    public void toJson() throws JsonProcessingException {
        ClientsStocktaking message = new ClientsStocktaking(List.of(identity));

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"ClientsStocktaking\",\"messageID\":\"" + message.getMessageID() + "\",\"identities\":[\"" + identity.getId() + "\"],\"signature\":null}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"ClientsStocktaking\",\"messageID\":\"FDCC13C730368155EC025866\",\"identities\":[\"" + identity.getId() + "\"],\"signature\":null}";

        assertThat(JSON_MAPPER.readValue(json, IMessage.class), instanceOf(ClientsStocktaking.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new ClientsStocktaking(null);
        }, "ClientsStocktaking requires client UIDs");
    }

    @Test
    void testEquals() {
        ClientsStocktaking message1 = new ClientsStocktaking(List.of(identity));
        ClientsStocktaking message2 = new ClientsStocktaking(List.of(identity));
        ClientsStocktaking message3 = new ClientsStocktaking(List.of(IdentityTestHelper.random()));

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        ClientsStocktaking message1 = new ClientsStocktaking(List.of(identity));
        ClientsStocktaking message2 = new ClientsStocktaking(List.of(identity));
        ClientsStocktaking message3 = new ClientsStocktaking(List.of(IdentityTestHelper.random()));

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
