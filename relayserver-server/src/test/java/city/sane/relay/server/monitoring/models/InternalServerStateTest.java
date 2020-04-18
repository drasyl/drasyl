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

package city.sane.relay.server.monitoring.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InternalServerStateTest {
    private long bootTime;
    private String ip;
    private long pendingFutures;
    private String uid;
    private long totalFailedMessages;
    private long totalReceivedMessages;
    private long totalSentMessages;
    private String ua;
    private long timeoutedFutures;
    private Collection<String> deadClients;
    private Collection<InternalClientState> localClients;
    private Collection<InternalClientState> relays;
    private Collection<RemoteClientState> remoteClients;
    private String configs;

    @BeforeEach
    void setUp() {
        bootTime = 1000L;
        ip = "ip";
        pendingFutures = 2L;
        uid = "uid";
        totalFailedMessages = 2L;
        totalReceivedMessages = 1L;
        totalSentMessages = 1L;
        ua = "ua";
        timeoutedFutures = 30L;
        configs = "configs";
        deadClients = new ArrayList<>();
        localClients = new ArrayList<>();
        relays = new ArrayList<>();
        remoteClients = new ArrayList<>();
    }

    @Test
    void testObjectCreation() {
        InternalServerState state = new InternalServerState();

        state.setDeadClients(deadClients);
        state.setTotalFailedMessages(totalFailedMessages);
        state.setBootTime(bootTime);
        state.setConfigs(configs);
        state.setIP(ip);
        state.setLocalClients(localClients);
        state.setPendingFutures(pendingFutures);
        state.setRelays(relays);
        state.setRemoteClients(remoteClients);
        state.setSystemUID(uid);
        state.setTimeoutedFutures(timeoutedFutures);
        state.setTotalReceivedMessages(totalReceivedMessages);
        state.setUA(ua);
        state.setTotalSentMessages(totalSentMessages);

        assertEquals(deadClients, state.getDeadClients());
        assertEquals(totalFailedMessages, state.getTotalFailedMessages());
        assertEquals(bootTime, state.getBootTime());
        assertEquals(configs, state.getConfigs());
        assertEquals(ip, state.getIP());
        assertEquals(localClients, state.getLocalClients());
        assertEquals(pendingFutures, state.getPendingFutures());
        assertEquals(relays, state.getRelays());
        assertEquals(remoteClients, state.getRemoteClients());
        assertEquals(uid, state.getSystemUID());
        assertEquals(timeoutedFutures, state.getTimeoutedFutures());
        assertEquals(totalReceivedMessages, state.getTotalReceivedMessages());
        assertEquals(ua, state.getUA());
        assertEquals(totalSentMessages, state.getTotalSentMessages());
    }
}