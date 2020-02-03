/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.monitoring;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import org.drasyl.all.DrasylConfig;
import org.drasyl.all.DrasylException;
import org.drasyl.all.session.Session;
import org.drasyl.all.session.util.AutoDeletionBucket;
import org.drasyl.all.session.util.ClientSessionBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.Sets;
import com.typesafe.config.ConfigFactory;

import org.drasyl.all.models.SessionUID;
import org.drasyl.all.models.IPAddress;
import org.drasyl.all.Drasyl;
import org.drasyl.all.monitoring.models.InternalServerState;
import org.mockito.internal.util.reflection.FieldSetter;

public class AggregatorTest {
    private Aggregator classUnderTest;

    @Mock
    private Drasyl mockRelay;
    @Mock
    private ClientSessionBucket mockBucket;
//    @Mock
//    private RelayP2PAgent mockP2PAgent;
    @Mock
    private Session mockClient;

    private IPAddress ip = new IPAddress("127.0.0.1:22527");
    private SessionUID relayUID = SessionUID.of("testRelayUID");
//    private long pendingInMsg = 10L;
//    private long pendingOutMsg = 15L;
    private long pendingFutures = 2L;
    private long timeoutedFutures = 1L;

    private SessionUID remoteClientUID = SessionUID.of("remoteClientUID");
    private SessionUID localClientUID = SessionUID.of("clientUID");
//    private SessionUID peerID = SessionUID.of("peerUID");

    private IPAddress clientsIP = new IPAddress("127.0.0.1:443");

    @Mock
    private AutoDeletionBucket<Session> deadClients;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        classUnderTest = mock(Aggregator.class, Mockito.CALLS_REAL_METHODS);
        FieldSetter.setField(classUnderTest, Aggregator.class.getDeclaredField("relay"), mockRelay);

        when(mockRelay.getConfig()).thenReturn(new DrasylConfig(ConfigFactory.load()));
        when(mockRelay.getUID()).thenReturn(relayUID);
        when(mockRelay.getExternalIP()).thenReturn(ip);
        when(mockRelay.getClientBucket()).thenReturn(mockBucket);
        when(mockRelay.getDeadClientsBucket()).thenReturn(deadClients);
//        when(mockRelay.getRelayP2PAgent()).thenReturn(mockP2PAgent);

        when(mockBucket.getLocalClientSessions()).thenReturn(Sets.newHashSet(mockClient));
        when(mockBucket.getRemoteClientUIDs()).thenReturn(Sets.newHashSet(remoteClientUID));

//        when(mockP2PAgent.getPeers()).thenReturn(Map.of(peerID, mockClient));

        when(mockClient.getUID()).thenReturn(localClientUID);
        when(mockClient.getBootTime()).thenReturn(1000L);
        when(mockClient.getTargetSystem()).thenReturn(clientsIP);
        when(mockClient.isTerminated()).thenReturn(false);
//        when(mockClient.pendingInMsg()).thenReturn((int) pendingInMsg);
//        when(mockClient.pendingOutMsg()).thenReturn((int) pendingOutMsg);
        when(mockClient.pendingFutures()).thenReturn(pendingFutures);
        when(mockClient.timeoutedFutures()).thenReturn(timeoutedFutures);
    }

    @Test
    public void getSystemStatusTest() throws DrasylException {
        classUnderTest.getSystemStatus();

        InternalServerState state = classUnderTest.state;

//        assertEquals(pendingInMsg*2, state.getPendingInMsg());
//        assertEquals(pendingOutMsg*2, state.getPendingOutMsg());
        assertEquals(pendingFutures, state.getPendingFutures());
        assertEquals(timeoutedFutures, state.getTimeoutedFutures());
    }
}
