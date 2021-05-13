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
import org.drasyl.identity.IdentityPublicKey;
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
    private final Set<IdentityPublicKey> members = new HashSet<>();

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
