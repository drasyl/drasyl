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
package org.drasyl.plugin.groups.client;

import com.typesafe.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.drasyl.plugin.groups.client.GroupsClientConfig.GROUPS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientConfigTest {
    private Set<GroupUri> groups;
    @Mock
    private Config typesafeConfig;

    @BeforeEach
    void setUp() {
        groups = Set.of(GroupUri.of("groups://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60"));
    }

    @Nested
    class Constructor {
        @Test
        void shouldReadConfigProperly() {
            when(typesafeConfig.getStringList(GROUPS)).thenReturn(List.of("groups://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group?timeout=60"));

            final GroupsClientConfig config = new GroupsClientConfig(typesafeConfig);

            assertEquals(groups, config.getGroups());
        }

        @Test
        void shouldReadFromBuilderProperly() {
            final GroupsClientConfig config = GroupsClientConfig.newBuilder().addGroup(groups.iterator().next()).build();

            assertEquals(groups, config.getGroups());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupsClientConfig config1 = GroupsClientConfig.newBuilder().addGroup(groups.iterator().next()).build();
            final GroupsClientConfig config2 = GroupsClientConfig.newBuilder().addGroup(groups.iterator().next()).build();

            assertEquals(config1, config2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupsClientConfig config1 = GroupsClientConfig.newBuilder().addGroup(
                    GroupUri.of("groups://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group1?timeout=60")).build();
            final GroupsClientConfig config2 = GroupsClientConfig.newBuilder().addGroup(
                    GroupUri.of("groups://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group2?timeout=60")).build();

            assertNotEquals(config1, config2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupsClientConfig config1 = GroupsClientConfig.newBuilder().addGroup(groups.iterator().next()).build();
            final GroupsClientConfig config2 = GroupsClientConfig.newBuilder().addGroup(groups.iterator().next()).build();

            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupsClientConfig config1 = GroupsClientConfig.newBuilder().addGroup(
                    GroupUri.of("groups://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group1?timeout=60")).build();
            final GroupsClientConfig config2 = GroupsClientConfig.newBuilder().addGroup(
                    GroupUri.of("groups://secret@03678023dfecac5f2217cb6f6665ad38af3d75cc5d979829a3b091a2b4b2654e5b/group2?timeout=60")).build();

            assertNotEquals(config1.hashCode(), config2.hashCode());
        }
    }
}
