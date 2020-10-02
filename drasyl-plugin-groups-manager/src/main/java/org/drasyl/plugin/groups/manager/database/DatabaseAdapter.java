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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
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
     * @return true if the group was added, false if it already exist
     * @throws DatabaseException if an error occurs during the execution
     */
    boolean addGroup(final Group group) throws DatabaseException;

    /**
     * Adds or updates the given {@code groupMember#member} to the given {@code groupMember#group}.
     *
     * @param membership the entry that should be added or updated
     * @return true if the member was inserted and not updated
     * @throws DatabaseException if an error occurs during the execution
     */
    boolean addGroupMember(final Membership membership) throws DatabaseException;

    /**
     * Returns the group with the specified {@code name} or null if it could not be found.
     *
     * @param name the name of the group
     * @return group or null if it could not be found
     */
    Group getGroup(final String name);

    /**
     * Returns the members of the group {@code name} as set.
     *
     * @param name the group name
     * @return members of the group as set
     */
    Set<Membership> getGroupMembers(final String name);

    /**
     * Removes the {@code member} from the given {@code group}.
     *
     * @param member    the member to remove
     * @param groupName the group
     * @throws DatabaseException if an exception occurs during removal
     */
    void removeGroupMember(final CompressedPublicKey member,
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