/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node.plugin;

import org.drasyl.EmbeddedNode;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.plugin.groups.client.GroupUri;
import org.drasyl.node.plugin.groups.client.GroupsClientConfig;
import org.drasyl.node.plugin.groups.client.GroupsClientPlugin;
import org.drasyl.node.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.node.plugin.groups.client.event.GroupMemberJoinedEvent;
import org.drasyl.node.plugin.groups.client.event.GroupMemberLeftEvent;
import org.drasyl.node.plugin.groups.manager.GroupsManagerConfig;
import org.drasyl.node.plugin.groups.manager.GroupsManagerPlugin;
import org.drasyl.node.plugin.groups.manager.data.Group;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;
import static test.util.IdentityTestUtil.ID_3;

class GroupsPluginIT {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsPluginIT.class);
    private Group group;
    private EmbeddedNode manager;
    private EmbeddedNode client1;
    private EmbeddedNode client2;

    @BeforeEach
    void setUp() throws DrasylException {
        DrasylConfig config;

        // manager
        group = Group.of("my-fancy-group", "s3cr3t_passw0rd!");
        config = DrasylConfig.newBuilder()
                .networkId(0)
                .identity(ID_1)
                .remoteExposeEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(22528)
                .remotePingInterval(ofSeconds(1))
                .remotePingTimeout(ofSeconds(2))
                .remoteSuperPeerEnabled(false)
                .remoteStaticRoutes(Map.of(
                        ID_2.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22529),
                        ID_3.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22530)
                ))
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .plugins(Set.of(new GroupsManagerPlugin(GroupsManagerConfig.newBuilder().groups(Map.of(group.getName(), group)).build())))
                .build();
        manager = new EmbeddedNode(config).awaitStarted();
        final GroupUri groupUri = group.getUri(manager.identity().getIdentityPublicKey());
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED/STARTED manager"));

        // client1
        config = DrasylConfig.newBuilder()
                .networkId(0)
                .identity(ID_2)
                .remoteExposeEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(22529)
                .remotePingInterval(ofSeconds(1))
                .remotePingTimeout(ofSeconds(2))
                .remoteSuperPeerEnabled(false)
                .remoteStaticRoutes(Map.of(
                        ID_1.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22528),
                        ID_3.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22530)
                ))
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .plugins(Set.of(new GroupsClientPlugin(GroupsClientConfig.newBuilder().groups(Set.of(groupUri)).build())))
                .build();
        client1 = new EmbeddedNode(config);
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client1"));

        // client2
        config = DrasylConfig.newBuilder()
                .networkId(0)
                .identity(ID_3)
                .remoteExposeEnabled(false)
                .remoteBindHost(createInetAddress("127.0.0.1"))
                .remoteBindPort(22530)
                .remotePingInterval(ofSeconds(1))
                .remotePingTimeout(ofSeconds(2))
                .remoteSuperPeerEnabled(false)
                .remoteStaticRoutes(Map.of(
                        ID_1.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22528),
                        ID_2.getIdentityPublicKey(), new InetSocketAddress("127.0.0.1", 22529)
                ))
                .remoteLocalHostDiscoveryEnabled(false)
                .remoteTcpFallbackEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .intraVmDiscoveryEnabled(false)
                .plugins(Set.of(new GroupsClientPlugin(GroupsClientConfig.newBuilder().groups(Set.of(groupUri)).build())))
                .build();
        client2 = new EmbeddedNode(config);
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "CREATED client2"));
    }

    @AfterEach
    void tearDown() {
        manager.close();
        client1.close();
        client2.close();
    }

    @Test
    void shouldEmitCorrectEventsToClients() {
        //
        // first client join group
        //
        client1.awaitStarted();

        // client1: check for join confirmation and join notification
        await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(GroupJoinedEvent.class)));
        await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(GroupMemberJoinedEvent.class)));

        //
        // second client join group
        //
        client2.awaitStarted();

        // client1: check for join notification
        await().untilAsserted(() -> assertThat(client1.readEvent(), instanceOf(GroupMemberJoinedEvent.class)));

        // client2: check for join confirmation and join notification
        await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(GroupJoinedEvent.class)));
        await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(GroupMemberJoinedEvent.class)));

        //
        // first client left group
        //
        client1.close();

        // client2: check for leave notification
        await().untilAsserted(() -> assertThat(client2.readEvent(), instanceOf(GroupMemberLeftEvent.class)));
    }
}
