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
package org.drasyl.plugin.groups.client;

import com.typesafe.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

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
    private String id;

    @BeforeEach
    void setUp() {
        id = IdentityTestUtil.ID_1.getIdentityPublicKey().toString();
        groups = Set.of(GroupUri.of("groups://secret@" + id + "/group?timeout=60"));
    }

    @Nested
    class Constructor {
        @Test
        void shouldReadConfigProperly() {
            when(typesafeConfig.getStringList(GROUPS)).thenReturn(List.of("groups://secret@" + id + "/group?timeout=60"));

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
                    GroupUri.of("groups://secret@" + id + "/group1?timeout=60")).build();
            final GroupsClientConfig config2 = GroupsClientConfig.newBuilder().addGroup(
                    GroupUri.of("groups://secret@" + id + "/group2?timeout=60")).build();

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
                    GroupUri.of("groups://secret@" + id + "/group1?timeout=60")).build();
            final GroupsClientConfig config2 = GroupsClientConfig.newBuilder().addGroup(
                    GroupUri.of("groups://secret@" + id + "/group2?timeout=60")).build();

            assertNotEquals(config1.hashCode(), config2.hashCode());
        }
    }
}
