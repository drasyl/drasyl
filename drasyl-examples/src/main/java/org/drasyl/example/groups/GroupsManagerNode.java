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

package org.drasyl.example.groups;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.plugin.groups.client.GroupsClientPlugin;
import org.drasyl.plugin.groups.manager.GroupsManagerConfig;
import org.drasyl.plugin.groups.manager.GroupsManagerPlugin;
import org.drasyl.plugin.groups.manager.data.Group;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * This example starts a drasyl node with activated {@link GroupsManagerPlugin}.
 */
@SuppressWarnings({ "squid:S106" })
public class GroupsManagerNode {
    public static void main(final String[] args) throws DrasylException {
        final Group group = Group.of("my-fancy-group", "s3cr3t_passw0rd!");
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of("groups-manager.identity.json"))
                .remoteBindPort(0)
                .plugins(Set.of(new GroupsManagerPlugin(
                        GroupsManagerConfig.builder()
//                            .databaseUri(URI.create("jdbc:sqlite:groups-manager.sqlite"))
                                .groups(Map.of(group.getName(), group))
                                .build()
                )))
                .build();

        final DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(final Event event) {
                System.out.println("event = " + event);
                if (event instanceof NodeOnlineEvent) {
                    System.out.println("Node is online! Nodes can now join the group '" + group.getName() + "' by adding '" + group.getUri(identity().getPublicKey()).toUri() + "' to your config (drasyl.config.plugins.\"" + GroupsClientPlugin.class.getName() + "\".groups).");
                }
            }
        };
        node.start().join();
    }
}
