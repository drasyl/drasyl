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

import java.net.URI;
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
                .serverBindPort(0)
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
