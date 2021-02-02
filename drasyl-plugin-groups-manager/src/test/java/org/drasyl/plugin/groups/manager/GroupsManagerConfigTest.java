/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin.groups.manager;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.drasyl.plugin.groups.manager.data.Group;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;

import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_BIND_HOST;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_BIND_PORT;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_ENABLED;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.DATABASE_URI;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.GROUPS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsManagerConfigTest {
    private URI databaseURI;
    private Map<String, Group> groups;
    private Group group;
    @Mock
    private Config typesafeConfig;

    @BeforeEach
    void setUp() {
        databaseURI = URI.create("jdbc:sqlite:file:groups?mode=memory&cache=shared");
        group = Group.of("name", "secret", (byte) 0, Duration.ofSeconds(60));
        groups = Map.of("name", group);
    }

    @Nested
    class Constructor {
        @Test
        void shouldReadConfigProperly() throws UnknownHostException {
            when(typesafeConfig.getString(DATABASE_URI)).thenReturn("jdbc:sqlite:file:groups?mode=memory&cache=shared");
            when(typesafeConfig.getObject(GROUPS)).thenReturn(ConfigValueFactory.fromMap(Map.of("name",
                    Map.of("secret", "secret",
                            "min-difficulty", 0,
                            "timeout", Duration.ofSeconds(60)))));
            when(typesafeConfig.getBoolean(API_ENABLED)).thenReturn(true);
            when(typesafeConfig.getString(API_BIND_HOST)).thenReturn("0.0.0.0");
            when(typesafeConfig.getInt(API_BIND_PORT)).thenReturn(8080);

            final GroupsManagerConfig config = new GroupsManagerConfig(typesafeConfig);

            assertEquals(databaseURI, config.getDatabaseUri());
            assertEquals(groups, config.getGroups());
            assertTrue(config.isApiEnabled());
            assertEquals(InetAddress.getByName("0.0.0.0"), config.getApiBindHost());
            assertEquals(8080, config.getApiBindPort());
        }

        @Test
        void shouldReadFromBuilderProperly() throws UnknownHostException {
            final GroupsManagerConfig config = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();

            assertEquals(databaseURI, config.getDatabaseUri());
            assertEquals(groups, config.getGroups());
            assertTrue(config.isApiEnabled());
            assertEquals(InetAddress.getByName("0.0.0.0"), config.getApiBindHost());
            assertEquals(8080, config.getApiBindPort());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() throws UnknownHostException {
            final GroupsManagerConfig config1 = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();

            assertEquals(config1, config2);
        }

        @Test
        void shouldNotBeEquals() throws UnknownHostException {
            final GroupsManagerConfig config1 = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.builder()
                    .databaseUri(URI.create(""))
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();

            assertNotEquals(config1, config2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() throws UnknownHostException {
            final GroupsManagerConfig config1 = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();

            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        void shouldNotBeEquals() throws UnknownHostException {
            final GroupsManagerConfig config1 = GroupsManagerConfig.builder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.builder()
                    .databaseUri(URI.create(""))
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();

            assertNotEquals(config1.hashCode(), config2.hashCode());
        }
    }
}
