/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.node.plugin.groups.manager;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.drasyl.node.plugin.groups.manager.data.Group;
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

import static org.drasyl.node.plugin.groups.manager.GroupsManagerConfig.API_BIND_HOST;
import static org.drasyl.node.plugin.groups.manager.GroupsManagerConfig.API_BIND_PORT;
import static org.drasyl.node.plugin.groups.manager.GroupsManagerConfig.API_ENABLED;
import static org.drasyl.node.plugin.groups.manager.GroupsManagerConfig.DATABASE_URI;
import static org.drasyl.node.plugin.groups.manager.GroupsManagerConfig.GROUPS;
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
            final GroupsManagerConfig config = GroupsManagerConfig.newBuilder()
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
            final GroupsManagerConfig config1 = GroupsManagerConfig.newBuilder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.newBuilder()
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
            final GroupsManagerConfig config1 = GroupsManagerConfig.newBuilder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.newBuilder()
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
            final GroupsManagerConfig config1 = GroupsManagerConfig.newBuilder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.newBuilder()
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
            final GroupsManagerConfig config1 = GroupsManagerConfig.newBuilder()
                    .databaseUri(databaseURI)
                    .groups(groups)
                    .apiEnabled(true)
                    .apiBindHost(InetAddress.getByName("0.0.0.0"))
                    .apiBindPort(8080)
                    .build();
            final GroupsManagerConfig config2 = GroupsManagerConfig.newBuilder()
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
