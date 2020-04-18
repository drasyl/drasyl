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

package city.sane.relay.common.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class IPAddressTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        IPAddress ip = new IPAddress("host:80");

        assertThatJson(JSON_MAPPER.writeValueAsString(ip))
                .isEqualTo("\"host:80\"");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "\"host:80\"";

        IPAddress ipAddress = JSON_MAPPER.readValue(json, IPAddress.class);

        assertThat(ipAddress.getHost(), is("host"));
        assertThat(ipAddress.getPort(), is(80));
    }

    @Test
    public void invalidIPAddressTest() throws IllegalArgumentException {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new IPAddress("host");
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            new IPAddress(null, 80);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new IPAddress("host", -2);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new IPAddress("host", 65536);
        });
    }
}
