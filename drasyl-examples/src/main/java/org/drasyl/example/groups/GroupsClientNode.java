/*
 * Copyright (c) 2021.
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
import org.drasyl.plugin.groups.client.GroupUri;
import org.drasyl.plugin.groups.client.GroupsClientConfig;
import org.drasyl.plugin.groups.client.GroupsClientPlugin;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;

/**
 * This example starts a drasyl node with activated {@link GroupsClientNode}. It can be used to join
 * groups provided by {@link GroupsManagerNode}.
 */
@SuppressWarnings({ "squid:S106" })
public class GroupsClientNode {
    public static void main(final String[] args) throws DrasylException {
        final Scanner scanner = new Scanner(System.in);
        System.out.print("Please enter URI printed by " + GroupsManagerNode.class.getSimpleName() + ": ");
        final String input = scanner.nextLine();
        final GroupUri group = GroupUri.of(input);

        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of("groups-client.identity.json"))
                .remoteBindPort(0)
                .plugins(Set.of(new GroupsClientPlugin(
                        GroupsClientConfig.builder()
                                .groups(Set.of(group))
                                .build()
                )))
                .build();

        final DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(final @NonNull Event event) {
                System.out.println("event = " + event);
                if (event instanceof GroupJoinedEvent) {
                    System.out.println("Node has successfully joined group '" + ((GroupJoinedEvent) event).getGroup().getName() + "'");
                }
            }
        };
        node.start().join();
    }
}
