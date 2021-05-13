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
package org.drasyl.plugin.groups.manager.database;

import org.drasyl.identity.IdentityPublicKey;
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
    boolean removeGroupMember(final IdentityPublicKey member,
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
