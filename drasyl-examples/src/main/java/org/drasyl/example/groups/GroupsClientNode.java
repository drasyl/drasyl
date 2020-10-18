package org.drasyl.example.groups;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.plugin.groups.client.GroupUri;
import org.drasyl.plugin.groups.client.GroupsClientConfig;
import org.drasyl.plugin.groups.client.GroupsClientPlugin;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;

/**
 * This example starts a drasyl node with activated {@link GroupsClientNode}.
 * It can be used to join groups provided by {@link GroupsManagerNode}.
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
                .serverBindPort(0)
                .plugins(Set.of(new GroupsClientPlugin(
                        GroupsClientConfig.builder()
                                .groups(Set.of(group))
                                .build()
                )))
                .build();

        final DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(final Event event) {
                System.out.println("event = " + event);
                if (event instanceof GroupJoinedEvent) {
                    System.out.println("Node has successfully joined group '" + ((GroupJoinedEvent) event).getGroup().getName() + "'");
                }
            }
        };
        node.start().join();
    }
}
