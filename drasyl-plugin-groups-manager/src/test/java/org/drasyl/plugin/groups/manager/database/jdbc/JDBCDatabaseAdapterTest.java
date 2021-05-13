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
package org.drasyl.plugin.groups.manager.database.jdbc;

import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.data.Member;
import org.drasyl.plugin.groups.manager.data.Membership;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class JDBCDatabaseAdapterTest {
    private JDBCDatabaseAdapter database;
    private IdentityPublicKey publicKey;

    @BeforeEach
    void setUp() throws DatabaseException {
        database = new JDBCDatabaseAdapter(URI.create("jdbc:sqlite::memory:"));
        publicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
    }

    @AfterEach
    void tearDown() throws DatabaseException {
        database.close();
    }

    @SuppressWarnings({ "SqlDialectInspection", "SqlNoDataSourceInspection" })
    @Nested
    class Create {
        @Test
        void shouldCreateTables() {
            try (final Connection con = database.getConnection()) {
                final String sqlMember = "SELECT count(*) AS count FROM sqlite_master WHERE type='table' AND name='Member'";
                final String sqlGroup = "SELECT count(*) AS count FROM sqlite_master WHERE type='table' AND name='Group'";
                final String sqlGroupMembers = "SELECT count(*) AS count FROM sqlite_master WHERE type='table' AND name='GroupMembers'";

                try (final Statement statement = con.createStatement()) {
                    assertEquals(1, statement.executeQuery(sqlMember).getInt("count"));
                    assertEquals(1, statement.executeQuery(sqlGroup).getInt("count"));
                    assertEquals(1, statement.executeQuery(sqlGroupMembers).getInt("count"));
                }
            }
            catch (final SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Nested
    class AddGroup {
        @Test
        void shouldReturnTrueForNewGroup() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));

            // test
            assertTrue(database.addGroup(group));
        }

        @Test
        void shouldReturnFalseOnAlreadyExistingGroup() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            database.addGroup(group);

            // test
            assertFalse(database.addGroup(group));
        }
    }

    @Nested
    class AddGroupMember {
        @Test
        void shouldReturnTrueForNewMember() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);
            database.addGroup(group);

            // test
            assertTrue(database.addGroupMember(membership));
        }

        @Test
        void shouldReturnFalseOnAlreadyExistingMembers() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);
            database.addGroup(group);
            database.addGroupMember(membership);

            // test
            assertFalse(database.addGroupMember(membership));
        }
    }

    @Nested
    class GetGroup {
        @Test
        void shouldReturnExistingGroup() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            database.addGroup(group);

            // test
            assertEquals(group, database.getGroup(group.getName()));
        }
    }

    @Nested
    class GetGroups {
        @Test
        void shouldReturnExistingGroups() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            database.addGroup(group);

            // test
            assertEquals(Set.of(group), database.getGroups());
        }

        @Test
        void shouldReturnNullOnNotExistingGroup() throws DatabaseException {
            assertNull(database.getGroup("void"));
        }
    }

    @Nested
    class DeleteGroup {
        @Test
        void shouldReturnTrueForExistingGroup() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            database.addGroup(group);

            // test
            assertTrue(database.deleteGroup("name"));
        }

        @Test
        void shouldReturnFalseForNonExistingGroup() throws DatabaseException {
            assertFalse(database.deleteGroup("name"));
        }
    }

    @Nested
    class UpdateGroup {
        @Test
        void shouldReturnTrueForExistingGroup() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            database.addGroup(group);

            // test
            assertTrue(database.updateGroup(Group.of("name", "secret", (byte) 0, ofSeconds(120))));
        }

        @Test
        void shouldReturnFalseForNonExistingGroup() throws DatabaseException {
            assertFalse(database.updateGroup(Group.of("name", "secret", (byte) 0, ofSeconds(120))));
        }
    }

    @Nested
    class GetGroupMembers {
        @Test
        void shouldReturnGroupMembers() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);
            database.addGroup(group);
            database.addGroupMember(membership);

            // test
            assertThat(database.getGroupMembers(group.getName()), contains(membership));
        }
    }

    @Nested
    class RemoveGroupMember {
        @Test
        void shouldReturnTrueForMembers() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);
            database.addGroup(group);
            database.addGroupMember(membership);

            // test
            assertTrue(database.removeGroupMember(publicKey, group.getName()));
        }

        @Test
        void shouldReturnFalseForNonMember() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);
            database.addGroup(group);

            // test
            assertFalse(database.removeGroupMember(publicKey, group.getName()));
        }
    }

    @Nested
    class DeleteStaleMemberships {
        @Test
        void shouldRemoveStaleMemberships() throws DatabaseException {
            // prepare
            final Group group = Group.of("name", "secret", (byte) 0, ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);
            database.addGroup(group);
            database.addGroupMember(membership);

            // test
            assertThat(database.deleteStaleMemberships(), contains(membership));
        }
    }
}
