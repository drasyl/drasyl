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
package org.drasyl.plugin.groups.manager.database.jdbc;

import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
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
    private CompressedPublicKey publicKey;

    @BeforeEach
    void setUp() throws DatabaseException, CryptoException {
        database = new JDBCDatabaseAdapter(URI.create("jdbc:sqlite::memory:"));
        publicKey = CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff");
    }

    @AfterEach
    void tearDown() throws DatabaseException {
        database.close();
    }

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
