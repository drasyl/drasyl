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
package org.drasyl.example.groups;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.plugin.groups.manager.GroupsManagerConfig;
import org.drasyl.plugin.groups.manager.GroupsManagerPlugin;
import org.drasyl.plugin.groups.manager.data.Group;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * This example starts a drasyl node with activated {@link GroupsManagerPlugin} managing a single
 * group.
 */
@SuppressWarnings({ "squid:S106", "java:S2096" })
public class GroupsManagerNode extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "groups-manager.identity.json");
    private final Group group;

    public GroupsManagerNode(final Group group) throws DrasylException {
        super(DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .plugins(Set.of(new GroupsManagerPlugin(
                        GroupsManagerConfig.newBuilder()
//                            .databaseUri(URI.create("jdbc:sqlite:groups-manager.sqlite"))
                                .groups(Map.of(group.getName(), group))
                                .build()
                )))
                .build());
        this.group = group;
    }

    @Override
    public void onEvent(@NonNull final Event event) {
        if (event instanceof NodeOnlineEvent) {
            System.out.println("Node is online! Other nodes can now join the group `" + group.getName() + "` by adding the following group url to the config:");
            System.out.println();
            System.out.println("  " + group.getUri(identity().getPublicKey()).toUri());
            System.out.println();
            System.out.println("More information: https://docs.drasyl.org/plugins/groups/#configuration");
        }
    }

    public static void main(final String[] args) throws DrasylException {
        final Group group = Group.of("my-fancy-group", "s3cr3t_passw0rd!");
        final GroupsManagerNode node = new GroupsManagerNode(group);
        node.start().join();
    }
}
