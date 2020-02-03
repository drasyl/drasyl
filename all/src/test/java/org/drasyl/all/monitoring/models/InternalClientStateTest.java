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

package org.drasyl.all.monitoring.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InternalClientStateTest {
    private long bootTime;
    private Set<String> channels;
    private boolean initialized;
    private String ip;
    private long pendingFutures;
    private boolean terminated;
    private String uid;
    private long totalFailedMessages;
    private long totalReceivedMessages;
    private long totalSentMessages;
    private String ua;
    private long timeoutedFutures;

    @BeforeEach
    void setUp() {
        bootTime = 1000L;
        channels = Set.of("channel");
        initialized = true;
        ip = "ip";
        pendingFutures = 2L;
        terminated = false;
        uid = "uid";
        totalFailedMessages = 2L;
        totalReceivedMessages = 1L;
        totalSentMessages = 1L;
        ua = "ua";
        timeoutedFutures = 30L;
    }

    @Test
    void testObjectCreation() {
        InternalClientState state = new InternalClientState();
        state.setBootTime(bootTime);
        state.setChannels(channels);
        state.setInitialized(initialized);
        state.setIP(ip);
        state.setPendingFutures(pendingFutures);
        state.setTerminated(terminated);
        state.setUID(uid);
        state.setTotalFailedMessages(totalFailedMessages);
        state.setTotalReceivedMessages(totalReceivedMessages);
        state.setTotalSentMessages(totalSentMessages);
        state.setUA(ua);
        state.setTimeoutedFutures(timeoutedFutures);

        assertEquals(bootTime, state.getBootTime());
        assertEquals(channels, state.getChannels());
        assertEquals(initialized, state.isInitialized());
        assertEquals(ip, state.getIP());
        assertEquals(pendingFutures, state.getPendingFutures());
        assertEquals(terminated, state.isTerminated());
        assertEquals(uid, state.getUID());
        assertEquals(totalFailedMessages, state.getTotalFailedMessages());
        assertEquals(totalReceivedMessages, state.getTotalReceivedMessages());
        assertEquals(totalSentMessages, state.getTotalSentMessages());
        assertEquals(ua, state.getUA());
        assertEquals(timeoutedFutures, state.getTimeoutedFutures());
    }
}