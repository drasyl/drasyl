/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SessionUIDTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        SessionUID uid = SessionUID.of("123");

        assertThatJson(JSON_MAPPER.writeValueAsString(uid))
                .isEqualTo("\"123\"");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "\"123\"";

        assertThat(JSON_MAPPER.readValue(json, SessionUID.class).getValue(), is("123"));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            SessionUID.of((String) null);
        }, "sessionUID requires a value");
    }

    @Test
    public void invalidUIDTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            SessionUID.of("#");
        }, "sessionUID must be valid");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            SessionUID.of("test#");
        }, "sessionUID must be valid");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            SessionUID.of("#test");
        }, "sessionUID must be valid");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            SessionUID.of("-.");
        }, "sessionUID must be valid");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            SessionUID.of("test#test2#");
        }, "sessionUID must be valid");
    }
}
