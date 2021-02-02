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
package org.drasyl.plugin.groups.manager.database;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.data.Membership;

import java.util.Set;

/**
 * This class is responsible for storing the groups plugin options.
 */
public interface DatabaseAdapter {
    /**
     * Adds a group to the database if it doesn't exist already.
     *
     * @param group the group that should be added
     * @return {@code true} if the group was added, {@code false} if it already exist
     * @throws DatabaseException if an error occurs during the execution
     */
    boolean addGroup(final Group group) throws DatabaseException;

    /**
     * Adds or updates the given {@code groupMember#member} to the given {@code groupMember#group}.
     *
     * @param membership the entry that should be added or updated
     * @return {@code true} if the member was inserted and not updated
     * @throws DatabaseException if an error occurs during the execution
     */
    boolean addGroupMember(final Membership membership) throws DatabaseException;

    /**
     * Returns the group with the specified {@code name} or {@code null} if it could not be found.
     *
     * @param name the name of the group
     * @return group or {@code null} if it could not be found
     * @throws DatabaseException if an error occurs during the deletion
     */
    Group getGroup(final String name) throws DatabaseException;

    /**
     * Lists all groups.
     *
     * @return List with all groups
     * @throws DatabaseException if an error occurs during the execution
     */
    Set<Group> getGroups() throws DatabaseException;

    /**
     * Deleted the group with the specified {@code name}.
     *
     * @param name the name of the group
     * @return {@code true} if group was deleted. Otherwise {@code false}.
     * @throws DatabaseException if an error occurs during removal
     */
    boolean deleteGroup(final String name) throws DatabaseException;

    /**
     * Updates the specified {@code group}.
     *
     * @param group the new desired group state (name is unchangeable)
     * @return {@code true} if group was updated. Otherwise {@code false}.
     * @throws DatabaseException if an error occurs during update
     */
    boolean updateGroup(Group group) throws DatabaseException;

    /**
     * Returns the members of the group {@code name} as set.
     *
     * @param name the group name
     * @return members of the group as set
     * @throws DatabaseException if an error occurs during the execution
     */
    Set<Membership> getGroupMembers(final String name) throws DatabaseException;

    /**
     * Removes the {@code member} from the given {@code group}.
     *
     * @param member    the member to remove
     * @param groupName the group
     * @return {@code true} if memberships was deleted. Otherwise {@code false}.
     * @throws DatabaseException if an exception occurs during removal
     */
    boolean removeGroupMember(final CompressedPublicKey member,
                              final String groupName) throws DatabaseException;

    /**
     * Closes the connection to the database.
     *
     * @throws DatabaseException if an error occurs during closing
     */
    void close() throws DatabaseException;

    /**
     * Deletes all stale memberships from all groups and returns the deleted members.
     *
     * @throws DatabaseException if an error occurred during deletion
     */
    Set<Membership> deleteStaleMemberships() throws DatabaseException;
}
