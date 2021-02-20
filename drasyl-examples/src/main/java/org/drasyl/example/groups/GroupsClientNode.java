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
package org.drasyl.example.groups;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.GroupUri;
import org.drasyl.plugin.groups.client.GroupsClientConfig;
import org.drasyl.plugin.groups.client.GroupsClientPlugin;
import org.drasyl.plugin.groups.client.event.GroupEvent;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberLeftEvent;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * This example starts a drasyl node with activated {@link GroupsClientNode}. The node joins a given
 * group and then tracks the members of the group.
 */
@SuppressWarnings({ "squid:S106", "squid:S126", "java:S1943", "java:S2096" })
public class GroupsClientNode extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "groups-client.identity.json");
    private final Set<CompressedPublicKey> members = new HashSet<>();

    protected GroupsClientNode(final GroupUri group) throws DrasylException {
        super(DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .remoteBindPort(0)
                .plugins(Set.of(new GroupsClientPlugin(
                        GroupsClientConfig.newBuilder()
                                .groups(Set.of(group))
                                .build()
                )))
                .build());
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof GroupEvent) {
            if (event instanceof GroupJoinedEvent) {
                System.out.println("Node has successfully joined group `" + ((GroupEvent) event).getGroup().getName() + "`");
                members.addAll(((GroupJoinedEvent) event).getMembers());
                return;
            }
            else if (event instanceof GroupMemberJoinedEvent) {
                members.add(((GroupMemberJoinedEvent) event).getMember());
            }
            else if (event instanceof GroupMemberLeftEvent) {
                members.remove(((GroupMemberLeftEvent) event).getMember());
            }

            System.out.println("Group Members: " + members);
        }
    }

    public static void main(final String[] args) throws DrasylException {
        if (args.length != 1) {
            System.err.println("Please provide group url as first argument.");
            System.exit(1);
        }
        final GroupUri group = GroupUri.of(args[0]);

        final DrasylNode node = new GroupsClientNode(group);
        node.start().join();
    }
}
