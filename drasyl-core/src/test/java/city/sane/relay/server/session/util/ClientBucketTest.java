/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.server.session.util;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Set;

import city.sane.relay.common.models.SessionChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import city.sane.relay.common.models.SessionUID;
import city.sane.relay.server.session.Session;

public class ClientBucketTest {

    private static final SessionUID CLIENT_UID = SessionUID.of("clientUID");
    private static final SessionUID CLIENT_UID2 = SessionUID.of("clientUID2");
    private static final SessionUID REMOTE_SYSTEM_ID1 = SessionUID.of("remoteClientUID1");
    private static final SessionUID REMOTE_SYSTEM_ID2 = SessionUID.of("remoteClientUID2");
    private static final SessionUID REMOTE_SYSTEM_ID3 = SessionUID.of("remoteClientUID3");

    private ClientSessionBucket bucket;
    @Mock
    private Session client;
    @Mock
    private Session client2;

    @BeforeEach
    public void before() {
        MockitoAnnotations.initMocks(this);

        bucket = new ClientSessionBucket(SessionUID.of("relayUID"));

        when(client.getUID()).thenReturn(SessionUID.of("clientUID"));
        when(client.getUID()).thenReturn(SessionUID.of("clientUID2"));
    }

    @Test
    public void requireNonNullTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel1"), SessionChannel.of("testChannel2"));
        assertThrows(NullPointerException.class, () -> bucket.addLocalClientSession(null, client, sessionChannels));
        assertThrows(NullPointerException.class, () -> bucket.addLocalClientSession(CLIENT_UID, null, sessionChannels));
        assertThrows(NullPointerException.class,
                () -> bucket.addLocalClientSession(CLIENT_UID, client, (SessionChannel[]) null));
        assertThrows(NullPointerException.class,
                () -> bucket.addLocalClientSession(CLIENT_UID, client, (Set<SessionChannel>) null));
        assertThrows(NullPointerException.class, () -> bucket.addRemoteClient(null, sessionChannels));
        assertThrows(NullPointerException.class, () -> bucket.addRemoteClient(CLIENT_UID, null));
        assertThrows(NullPointerException.class, () -> bucket.aggregateChannelMembers(null));
        assertThrows(NullPointerException.class, () -> bucket.aggregateLocalChannelMembers(null));
        assertThrows(NullPointerException.class, () -> bucket.aggregateRemoteChannelMembers(null));
        assertThrows(NullPointerException.class, () -> bucket.getClientUIDsFromChannel(null));
        assertThrows(NullPointerException.class, () -> bucket.getChannelsFromClientUID(null));
        assertThrows(NullPointerException.class, () -> bucket.getLocalClientSession(null));
        assertThrows(NullPointerException.class, () -> bucket.removeClient(null));
        assertThrows(NullPointerException.class, () -> bucket.removeClients(null));
        assertThrows(NullPointerException.class, () -> bucket.transferLocalToRemote(null));
        assertThrows(NullPointerException.class, () -> bucket.transferRemoteToLocal(null, client));
        assertThrows(NullPointerException.class, () -> bucket.transferRemoteToLocal(CLIENT_UID, null));
        assertThrows(NullPointerException.class, () -> new ClientSessionBucket(null));
    }

    @Test
    public void addLocalClientTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));

        assertEquals(1, bucket.getClientUIDs().size());
        assertEquals(bucket.getLocalClientSessions().size(), bucket.getClientUIDs().size());
        assertEquals(client, bucket.getLocalClientSession(CLIENT_UID));
        assertEquals(sessionChannels, bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel2")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));
        assertTrue(bucket.getLocalClientSessions().contains(client));
    }

    @Test
    public void addMultipleLocalClientsTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));
        bucket.addLocalClientSession(CLIENT_UID2, client2, SessionChannel.of("testChannel"));

        assertEquals(2, bucket.getClientUIDs().size());
        assertEquals(bucket.getLocalClientSessions().size(), bucket.getClientUIDs().size());
        assertEquals(client, bucket.getLocalClientSession(CLIENT_UID));
        assertEquals(sessionChannels, bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID2));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel2")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));
        assertTrue(bucket.getLocalClientSessions().contains(client));
        assertTrue(bucket.getLocalClientSessions().contains(client2));
    }

    @Test
    public void addRemoteClientTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(CLIENT_UID, sessionChannels);

        assertEquals(0, bucket.getLocalClientSessions().size());
        assertEquals(1, bucket.getClientUIDs().size());
        assertEquals(sessionChannels, bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));
    }

    @Test
    public void addMultipleRemoteClientTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(CLIENT_UID, sessionChannels);
        bucket.addRemoteClient(CLIENT_UID2, sessionChannels);

        assertEquals(0, bucket.getLocalClientSessions().size());
        assertEquals(2, bucket.getClientUIDs().size());
        assertEquals(sessionChannels, bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));

        assertEquals(sessionChannels, bucket.getChannelsFromClientUID(CLIENT_UID2));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID2));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID2));
    }

    @Test
    public void addMultipleLocalAndRemoteClientsTest() {
        Set<SessionChannel> channelsRemote = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(REMOTE_SYSTEM_ID1, channelsRemote);
        bucket.addRemoteClient(REMOTE_SYSTEM_ID2, channelsRemote);

        assertEquals(channelsRemote, bucket.getChannelsFromClientUID(REMOTE_SYSTEM_ID1));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(REMOTE_SYSTEM_ID1));
        assertTrue(bucket.getClientUIDs().contains(REMOTE_SYSTEM_ID1));

        assertEquals(channelsRemote, bucket.getChannelsFromClientUID(REMOTE_SYSTEM_ID2));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(REMOTE_SYSTEM_ID2));
        assertTrue(bucket.getClientUIDs().contains(REMOTE_SYSTEM_ID2));

        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));
        bucket.addLocalClientSession(CLIENT_UID2, client2, SessionChannel.of("testChannel"));

        assertEquals(4, bucket.getClientUIDs().size());
        assertEquals(bucket.getLocalClientSessions().size(), bucket.getClientUIDs().size() - 2);
        assertEquals(client, bucket.getLocalClientSession(CLIENT_UID));
        assertEquals(sessionChannels, bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID2));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel2")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));
        assertTrue(bucket.getLocalClientSessions().contains(client));
        assertTrue(bucket.getLocalClientSessions().contains(client2));
    }

    @Test
    public void equalsTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));
        bucket.addLocalClientSession(CLIENT_UID, client2, SessionChannel.of("testChannel"));

        bucket.addRemoteClient(CLIENT_UID, sessionChannels);
        bucket.addRemoteClient(CLIENT_UID2, sessionChannels);

        ClientSessionBucket bucket2 = new ClientSessionBucket(SessionUID.of("relayUID"));
        bucket2.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));
        bucket2.addLocalClientSession(CLIENT_UID, client2, SessionChannel.of("testChannel"));

        bucket2.addRemoteClient(CLIENT_UID, sessionChannels);
        bucket2.addRemoteClient(CLIENT_UID2, sessionChannels);

        assertEquals(bucket, bucket2);
        assertEquals(bucket.hashCode(), bucket2.hashCode());
        assertEquals(bucket.getRemoteChannels(), bucket2.getRemoteChannels());
    }

    @Test
    public void aggregateLocalChannelMembersTest() {
        Set<SessionChannel> channelsRemote = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(REMOTE_SYSTEM_ID1, channelsRemote);
        bucket.addRemoteClient(REMOTE_SYSTEM_ID2, channelsRemote);

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));
        bucket.addLocalClientSession(CLIENT_UID2, client2, SessionChannel.of("testChannel"));

        assertTrue(bucket.aggregateLocalChannelMembers(CLIENT_UID).containsKey(CLIENT_UID));
        assertTrue(bucket.aggregateLocalChannelMembers(CLIENT_UID).containsValue(client));
        assertTrue(bucket.aggregateLocalChannelMembers(CLIENT_UID).containsKey(CLIENT_UID2));
        assertTrue(bucket.aggregateLocalChannelMembers(CLIENT_UID).containsValue(client2));
        assertTrue(bucket.aggregateChannelMembers(CLIENT_UID).getLeft().containsKey(CLIENT_UID));
        assertTrue(bucket.aggregateChannelMembers(CLIENT_UID).getLeft().containsValue(client));
        assertTrue(bucket.aggregateChannelMembers(CLIENT_UID).getLeft().containsKey(CLIENT_UID2));
        assertTrue(bucket.aggregateChannelMembers(CLIENT_UID).getLeft().containsValue(client2));
        assertEquals(2, bucket.aggregateLocalChannelMembers(CLIENT_UID).size());
        assertEquals(2, bucket.aggregateChannelMembers(CLIENT_UID).getLeft().size());
    }

    @Test
    public void aggregateRemoteChannelMembersTest() {
        Set<SessionChannel> channelsRemote = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(REMOTE_SYSTEM_ID1, channelsRemote);
        bucket.addRemoteClient(REMOTE_SYSTEM_ID2, channelsRemote);

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));
        bucket.addLocalClientSession(CLIENT_UID2, client2, SessionChannel.of("testChannel"));

        bucket.aggregateChannelMembers(CLIENT_UID).getRight().forEach(remoteClient -> {
            switch (remoteClient.getStringValue()) {
            case "remoteClientUID1":
            case "remoteClientUID2":
                assertTrue(true);
                break;
            default:
                assertTrue(false);
            }
        });

        assertTrue(bucket.aggregateRemoteChannelMembers(CLIENT_UID).contains(REMOTE_SYSTEM_ID1));
        assertTrue(bucket.aggregateRemoteChannelMembers(CLIENT_UID).contains(REMOTE_SYSTEM_ID2));
        assertEquals(2, bucket.aggregateRemoteChannelMembers(CLIENT_UID).size());
        assertEquals(2, bucket.aggregateChannelMembers(CLIENT_UID).getRight().size());
    }

    @Test
    public void removeTest() {
        Set<SessionChannel> channelsRemote = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(REMOTE_SYSTEM_ID1, channelsRemote);
        bucket.addRemoteClient(REMOTE_SYSTEM_ID2, channelsRemote);

        assertEquals(channelsRemote, bucket.getChannelsFromClientUID(REMOTE_SYSTEM_ID1));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(REMOTE_SYSTEM_ID1));
        assertTrue(bucket.getClientUIDs().contains(REMOTE_SYSTEM_ID1));

        assertEquals(channelsRemote, bucket.getChannelsFromClientUID(REMOTE_SYSTEM_ID2));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(REMOTE_SYSTEM_ID2));
        assertTrue(bucket.getClientUIDs().contains(REMOTE_SYSTEM_ID2));

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));
        bucket.addLocalClientSession(CLIENT_UID2, client2, SessionChannel.of("testChannel"));

        bucket.removeClient(CLIENT_UID2);
        bucket.removeClient(REMOTE_SYSTEM_ID2);

        assertEquals(2, bucket.getClientUIDs().size());
        assertEquals(bucket.getLocalClientSessions().size(), bucket.getClientUIDs().size() - 1);
        assertEquals(client, bucket.getLocalClientSession(CLIENT_UID));
        assertEquals(Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")),
                bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel2")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));
        assertTrue(bucket.getLocalClientSessions().contains(client));
    }

    @Test
    public void removeMultipleTest() {
        Set<SessionChannel> channelsRemote = Set.of(SessionChannel.of("testChannel"));

        bucket.addRemoteClient(REMOTE_SYSTEM_ID1, channelsRemote);
        bucket.addRemoteClient(REMOTE_SYSTEM_ID2, channelsRemote);
        bucket.addRemoteClient(REMOTE_SYSTEM_ID3, channelsRemote);

        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"),
                SessionChannel.of("testChannel"));
        bucket.addLocalClientSession(CLIENT_UID2, client2, SessionChannel.of("testChannel"));

        bucket.removeClients(Set.of(REMOTE_SYSTEM_ID1, REMOTE_SYSTEM_ID2, CLIENT_UID2));

        assertEquals(2, bucket.getClientUIDs().size());
        assertEquals(1, bucket.getLocalClientSessions().size());
        assertEquals(client, bucket.getLocalClientSession(CLIENT_UID));
        assertEquals(Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")),
                bucket.getChannelsFromClientUID(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel")).contains(REMOTE_SYSTEM_ID3));
        assertTrue(bucket.getClientUIDsFromChannel(SessionChannel.of("testChannel2")).contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(CLIENT_UID));
        assertTrue(bucket.getClientUIDs().contains(REMOTE_SYSTEM_ID3));
        assertTrue(bucket.getLocalClientSessions().contains(client));
    }

    @Test
    public void duplicateTest() {
        Set<SessionChannel> sessionChannels = Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2"));

        bucket.addLocalClientSession(CLIENT_UID, client, sessionChannels);
        bucket.addLocalClientSession(CLIENT_UID, client, SessionChannel.of("testChannel2"), SessionChannel.of("testChannel"));

        assertEquals(1, bucket.getLocalClientSessions().size());
    }
}
