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

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.data.Member;
import org.drasyl.plugin.groups.manager.data.Membership;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link DatabaseAdapter} implementation that supports SQL databases.
 */
@SuppressWarnings({
        "java:S109",
        "java:S1192",
        "java:S4174",
        "SqlDialectInspection",
        "SqlNoDataSourceInspection"
})
public class JDBCDatabaseAdapter implements DatabaseAdapter {
    public static final int QUERY_TIMEOUT = 15;
    public static final String SCHEME = "jdbc";
    private final String uri;
    private Connection connection;

    public JDBCDatabaseAdapter(final URI uri) throws DatabaseException {
        this.uri = uri.toString();

        createTables();
    }

    /**
     * Creates the initial tables for the database.
     *
     * @throws DatabaseException if an error occurs during the execution
     */
    private void createTables() throws DatabaseException {
        try (final Connection con = getConnection()) {
            final String sqlMembersTable = "CREATE TABLE IF NOT EXISTS `Member` ( publicKey TEXT PRIMARY KEY );";
            final String sqlGroupsTable = "CREATE TABLE IF NOT EXISTS `Group` ( name TEXT PRIMARY KEY, secret TEXT NOT NULL, minDifficulty INTEGER NOT NULL, timeout INTEGER NOT NULL );";
            final String sqlGroupMembersTable = "CREATE TABLE IF NOT EXISTS GroupMembers ( member TEXT NOT NULL, groupName TEXT NOT NULL, staleAt INTEGER NOT NULL, PRIMARY KEY (member, groupName), " +
                    "CONSTRAINT fk_member FOREIGN KEY (member) REFERENCES `Member` (publicKey) ON DELETE CASCADE, " +
                    "CONSTRAINT fk_group FOREIGN KEY (groupName) REFERENCES `Group` (name) ON DELETE CASCADE );";

            try (final Statement statement = con.createStatement()) {
                statement.setQueryTimeout(QUERY_TIMEOUT);
                statement.executeUpdate(sqlMembersTable);
                statement.executeUpdate(sqlGroupsTable);
                statement.executeUpdate(sqlGroupMembersTable);
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not create SQLite database", e);
        }
    }

    /**
     * Returns a database connection.
     *
     * @return a database connection
     * @throws SQLException if the connection could not be created
     */
    synchronized Connection getConnection() throws SQLException {
        if (connection == null) {
            connection = new SingleConnectionWrapper(DriverManager.getConnection(uri));
        }

        return connection;
    }

    @Override
    public boolean addGroup(final Group group) throws DatabaseException {
        try (final Connection con = getConnection()) {
            final String sqlInsertGroup = "INSERT OR IGNORE INTO `Group` (name, secret, minDifficulty, timeout) VALUES (?, ?, ?, ?);";

            try (final PreparedStatement ps = con.prepareStatement(sqlInsertGroup)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, group.getName());
                ps.setString(2, group.getCredentials());
                ps.setByte(3, group.getMinDifficulty());
                ps.setLong(4, group.getTimeout().toMillis());
                return ps.executeUpdate() > 0;
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not add group '" + group.getName() + "' to database", e);
        }
    }

    @Override
    public boolean addGroupMember(final Membership membership) throws DatabaseException {
        try (final Connection con = getConnection()) {
            final boolean rtn;
            final String sqlInsertMember = "INSERT OR IGNORE INTO `Member` (publicKey) VALUES (?);";
            final String sqlInsertGroupMember = "INSERT OR IGNORE INTO `GroupMembers` (member, groupName, staleAt) VALUES (?, ?, ?);";
            final String sqlUpdateGroupMember = "UPDATE `GroupMembers` SET staleAt=? WHERE member=? AND groupName=?;";

            try (final PreparedStatement ps = con.prepareStatement(sqlInsertMember)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, membership.getMember().getPublicKey().toString());
                ps.executeUpdate();
            }

            try (final PreparedStatement ps = con.prepareStatement(sqlInsertGroupMember)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, membership.getMember().getPublicKey().toString());
                ps.setString(2, membership.getGroup().getName());
                ps.setLong(3, membership.getStaleAt());
                rtn = ps.executeUpdate() > 0;
            }

            try (final PreparedStatement ps = con.prepareStatement(sqlUpdateGroupMember)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setLong(1, membership.getStaleAt());
                ps.setString(2, membership.getMember().getPublicKey().toString());
                ps.setString(3, membership.getGroup().getName());
                ps.executeUpdate();
            }

            return rtn;
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not add new member '" + membership.getMember().getPublicKey() + "' to group '" + membership.getGroup().getName() + "'", e);
        }
    }

    @Override
    public Group getGroup(final String name) throws DatabaseException {
        try (final Connection con = getConnection()) {
            final String sqlSelectGroup = "SELECT * FROM `Group` WHERE name=?";

            try (final PreparedStatement ps = con.prepareStatement(sqlSelectGroup)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, name);

                try (final ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Group.of(
                                rs.getString("name"),
                                rs.getString("secret"),
                                rs.getByte("minDifficulty"),
                                Duration.ofMillis(rs.getLong("timeout")));
                    }
                }
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not get group '" + name + "'", e);
        }

        return null;
    }

    @Override
    public Set<Group> getGroups() throws DatabaseException {
        final Set<Group> groups = new HashSet<>();

        try (final Connection con = getConnection()) {
            final String sqlSelectGroups = "SELECT * FROM `Group`;";

            try (final PreparedStatement ps = con.prepareStatement(sqlSelectGroups)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);

                try (final ResultSet rs = ps.executeQuery()) {
                    groups.addAll(getGroupsFromResultSet(rs));
                }
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not get groups", e);
        }

        return groups;
    }

    @Override
    public boolean deleteGroup(final String name) throws DatabaseException {
        try (final Connection con = getConnection()) {
            final String sqlDeleteGroup = "DELETE FROM `Group` WHERE name=?;";

            try (final PreparedStatement ps = con.prepareStatement(sqlDeleteGroup)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, name);

                return ps.executeUpdate() > 0;
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not remove group '" + name + "'", e);
        }
    }

    @Override
    public boolean updateGroup(final Group group) throws DatabaseException {
        try (final Connection con = getConnection()) {
            final String sqlUpdateGroup = "UPDATE `Group` SET secret=?, minDifficulty=?, timeout=? WHERE name=?;";

            try (final PreparedStatement ps = con.prepareStatement(sqlUpdateGroup)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, group.getCredentials());
                ps.setInt(2, group.getMinDifficulty());
                ps.setLong(3, group.getTimeout().toMillis());
                ps.setString(4, group.getName());
                return ps.executeUpdate() > 0;
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not update group '" + group.getName() + "'", e);
        }
    }

    @Override
    public Set<Membership> getGroupMembers(final String name) throws DatabaseException {
        final Set<Membership> members = new HashSet<>();

        try (final Connection con = getConnection()) {
            final String sqlSelectGroupMembers = "SELECT * FROM `GroupMembers` AS GM, `Group` AS G WHERE GM.groupName=? AND G.name=GM.groupName;";

            try (final PreparedStatement ps = con.prepareStatement(sqlSelectGroupMembers)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, name);

                try (final ResultSet rs = ps.executeQuery()) {
                    members.addAll(getGroupMembersFromResultSet(rs));
                }
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not get memberships of group '" + name + "'", e);
        }

        return members;
    }

    @Override
    public boolean removeGroupMember(final CompressedPublicKey member,
                                     final String groupName) throws DatabaseException {
        try (final Connection con = getConnection()) {
            final String sqlDeleteGroupMember = "DELETE FROM `GroupMembers` WHERE member=? AND groupName=?;";

            try (final PreparedStatement ps = con.prepareStatement(sqlDeleteGroupMember)) {
                ps.setQueryTimeout(QUERY_TIMEOUT);
                ps.setString(1, member.toString());
                ps.setString(2, groupName);

                return ps.executeUpdate() > 0;
            }
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not remove member '" + member.toString() + "' from group '" + groupName + "'", e);
        }
    }

    @Override
    public void close() throws DatabaseException {
        if (connection != null) {
            try {
                connection.close();
            }
            catch (final SQLException e) {
                throw new DatabaseException("Could close connection", e);
            }
        }
    }

    @Override
    public Set<Membership> deleteStaleMemberships() throws DatabaseException {
        final Set<Membership> rtn = new HashSet<>();
        final long time = System.currentTimeMillis();

        try (final Connection con = getConnection()) {
            return deleteStaleMembershipTransaction(con, time, rtn);
        }
        catch (final SQLException e) {
            throw new DatabaseException("Could not delete stale memberships", e);
        }
    }

    /**
     * Executes the actual transaction of the {@link #deleteStaleMemberships()} method.
     */
    private static Set<Membership> deleteStaleMembershipTransaction(final Connection con,
                                                                    final long time,
                                                                    final Set<Membership> rtn) throws SQLException, DatabaseException {
        final String sqlSelectAllStaleGroupMembers = "SELECT * FROM `GroupMembers` AS GM, `Group` AS G WHERE GM.staleAt<=? AND G.name=GM.groupName;";
        final String sqlDeleteAllStaleGroupMembers = "DELETE FROM `GroupMembers` WHERE staleAt <= ?;";
        // We want to do it in one transaction
        final boolean oldAutoCommit = con.getAutoCommit();

        try {
            con.setAutoCommit(false);

            try (final PreparedStatement ps = con.prepareStatement(sqlSelectAllStaleGroupMembers)) {
                ps.setLong(1, time);

                try (final ResultSet rs = ps.executeQuery()) {
                    rtn.addAll(getGroupMembersFromResultSet(rs));
                }
            }

            try (final PreparedStatement ps = con.prepareStatement(sqlDeleteAllStaleGroupMembers)) {
                ps.setLong(1, time);
                ps.executeUpdate();
            }

            return rtn;
        }
        catch (final SQLException e) {
            con.rollback();
            throw new DatabaseException("Could not delete stale memberships", e);
        }
        finally {
            con.commit();
            con.setAutoCommit(oldAutoCommit);
        }
    }

    private static Set<Group> getGroupsFromResultSet(final ResultSet rs) throws SQLException {
        final Set<Group> groups = new HashSet<>();

        while (rs.next()) {
            groups.add(Group.of(
                    rs.getString("name"),
                    rs.getString("secret"),
                    rs.getByte("minDifficulty"),
                    Duration.ofMillis(rs.getLong("timeout"))
            ));
        }

        return groups;
    }

    private static Set<Membership> getGroupMembersFromResultSet(final ResultSet rs) throws SQLException {
        final Set<Membership> members = new HashSet<>();

        while (rs.next()) {
            members.add(Membership.of(
                    Member.of(CompressedPublicKey.of(rs.getString("member"))),
                    Group.of(
                            rs.getString("name"),
                            rs.getString("secret"),
                            rs.getByte("minDifficulty"),
                            Duration.ofMillis(rs.getLong("timeout"))),
                    rs.getLong("staleAt")
            ));
        }

        return members;
    }
}
