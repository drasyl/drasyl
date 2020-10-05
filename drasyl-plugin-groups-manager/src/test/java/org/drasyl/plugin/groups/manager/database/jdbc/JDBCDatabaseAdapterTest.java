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
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    class Groups {
        @Test
        void shouldAddGroupAndReturnTheGroup() throws DatabaseException {
            final Group group = Group.of("name", "secret", (short) 0, Duration.ofSeconds(60));

            assertTrue(database.addGroup(group));
            assertEquals(group, database.getGroup(group.getName()));
        }

        @Test
        void shouldReturnFalseOnAlreadyExistingGroup() throws DatabaseException {
            final Group group = Group.of("name", "secret", (short) 0, Duration.ofSeconds(60));

            assertTrue(database.addGroup(group));
            assertFalse(database.addGroup(group));
            assertEquals(group, database.getGroup(group.getName()));
        }

        @Test
        void shouldReturnNullOnNotExistingGroup() {
            assertNull(database.getGroup("void"));
        }
    }

    @Nested
    class GroupMember {
        @Test
        void shouldAddAndReturnGroupMember() throws DatabaseException {
            final Group group = Group.of("name", "secret", (short) 0, Duration.ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);

            assertTrue(database.addGroup(group));
            assertTrue(database.addGroupMember(membership));
            assertThat(database.getGroupMembers(group.getName()), contains(membership));
        }

        @Test
        void shouldUpdateGroupMember() throws DatabaseException {
            final Group group = Group.of("name", "secret", (short) 0, Duration.ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership1 = Membership.of(member, group, 60);
            final Membership membership2 = Membership.of(member, group, 120);

            assertTrue(database.addGroup(group));

            assertTrue(database.addGroupMember(membership1));
            assertThat(database.getGroupMembers(group.getName()), contains(membership1));

            assertFalse(database.addGroupMember(membership2));
            assertThat(database.getGroupMembers(group.getName()), contains(membership2));

            assertNotEquals(membership1, membership2);
        }

        @Test
        void shouldRemoveGroupMember() throws DatabaseException {
            final Group group = Group.of("name", "secret", (short) 0, Duration.ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);

            assertTrue(database.addGroup(group));
            assertTrue(database.addGroupMember(membership));
            assertThat(database.getGroupMembers(group.getName()), contains(membership));
            database.removeGroupMember(publicKey, group.getName());
            assertThat(database.getGroupMembers(group.getName()), not(contains(membership)));
        }
    }

    @Nested
    class Stale {
        @Test
        void shouldRemoveStaleMemberships() throws DatabaseException {
            final Group group = Group.of("name", "secret", (short) 0, Duration.ofSeconds(60));
            final Member member = Member.of(publicKey);
            final Membership membership = Membership.of(member, group, 60);

            assertTrue(database.addGroup(group));
            assertTrue(database.addGroupMember(membership));
            assertThat(database.getGroupMembers(group.getName()), contains(membership));

            assertThat(database.deleteStaleMemberships(), contains(membership));
            assertThat(database.getGroupMembers(group.getName()), not(contains(membership)));
        }
    }
}