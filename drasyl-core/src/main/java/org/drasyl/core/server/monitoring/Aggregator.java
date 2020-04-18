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

package org.drasyl.core.server.monitoring;

import org.drasyl.core.common.messages.UserAgentMessage;
import org.drasyl.core.common.models.SessionChannel;
import org.drasyl.core.server.RelayServer;
import org.drasyl.core.server.RelayServerException;
import org.drasyl.core.server.monitoring.models.InternalClientState;
import org.drasyl.core.server.monitoring.models.InternalServerState;
import org.drasyl.core.server.monitoring.models.RemoteClientState;
import org.drasyl.core.server.session.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

@SuppressWarnings({"squid:CommentedOutCodeLine"})
public class Aggregator {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    protected InternalServerState state;

    private final RelayServer relay;
    private final String config;
    private final String ua;
    private final String uid;
    private final String ip;

    private long totalReceivedMessages;
    private long totalFailedMessages;
    private long totalSentMessages;
    private long timeoutedFutures;
    private long pendingFutures;

    public Aggregator(RelayServer relay) {
        this.relay = relay;
        this.config = getConfig();
        this.ua = UserAgentMessage.userAgentGenerator.get();
        this.uid = relay.getUID().getValue();
        this.ip = relay.getEntrypoint().toString();
    }

    private String getConfig() {
        String c = relay.getConfig().toString();

        return c.substring(c.indexOf('{'), c.lastIndexOf('}') + 1);
    }

    private void reset() {
        this.state = null;
        this.totalReceivedMessages = 0;
        this.totalFailedMessages = 0;
        this.totalSentMessages = 0;
        this.timeoutedFutures = 0;
        this.pendingFutures = 0;
    }

    /**
     * Aggregates the current system status.
     *
     * @return the status of this relay as json string
     */
    public String getSystemStatus() throws RelayServerException {
        reset();
        this.state = new InternalServerState();

        state.setUA(ua);
        state.setSystemUID(uid);
        state.setIP(ip);
        state.setConfigs(config);
        state.setBootTime(getSystemUptime(relay));

        getLocalClientsStatus(relay);
        getRemoteClientUIDs(relay);
//        getP2PConnectionStatus(relay);
        Collection<String> list = new ArrayList<>();
        for (Session session : relay.getDeadClientsBucket().getElements()) {
            list.add(session.getUID().getStringValue());
        }
        state.setDeadClients(list);

        state.setPendingFutures(pendingFutures);
        state.setTimeoutedFutures(timeoutedFutures);
        state.setTotalFailedMessages(totalFailedMessages);
        state.setTotalSentMessages(totalSentMessages);
        state.setTotalReceivedMessages(totalReceivedMessages);

        try {
            return JSON_MAPPER.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new RelayServerException(e);
        }
    }

    /**
     * @return the time how long the system has been running in ms.
     */
    protected long getSystemUptime(RelayServer relay) {
        return System.currentTimeMillis() - relay.getBootTime();
    }

    /**
     * Collects the states of the local clients.
     */
    protected void getLocalClientsStatus(RelayServer relay) {
        relay.getClientBucket().getLocalClientSessions().forEach(client -> {
            InternalClientState ics = new InternalClientState();

            collectClientStatus(client, ics);
            ics.setChannels(relay.getClientBucket().getChannelsFromClientUID(client.getUID()).stream()
                    .map(SessionChannel::getValue).collect(Collectors.toSet()));

            state.getLocalClients().add(ics);
        });
    }

    /**
     * Collects the state of the remote clients.
     */
    protected void getRemoteClientUIDs(RelayServer relay) {
        relay.getClientBucket().getRemoteClientUIDs().forEach(client -> {
            RemoteClientState rcs = new RemoteClientState();

            rcs.setUID(client.getValue());
            rcs.setChannels(relay.getClientBucket().getChannelsFromClientUID(client).stream().map(SessionChannel::getValue)
                    .collect(Collectors.toSet()));

            state.getRemoteClients().add(rcs);
        });
    }

    /**
     * @return a list of the status of all connected relay servers
     */
//    protected void getP2PConnectionStatus(RelayServer relay) {
//        relay.getRelayP2PAgent().getPeers()
//                .forEach((id, client) -> state.getRelays().add(collectClientStatus(client, new InternalClientState())));
//    }

    /**
     * Collects the status of a client.
     *
     * @param client the client
     */
    protected InternalClientState collectClientStatus(Session client, InternalClientState ics) {
        var tRM = client.getTotalReceivedMessages();
        var tFM = client.getTotalFailedMessages();
        var tSM = client.getTotalSentMessages();
        var tF = client.timeoutedFutures();
        var pF = client.pendingFutures();

        ics.setUID(client.getUID().getValue());
        ics.setBootTime(System.currentTimeMillis() - client.getBootTime());
        ics.setIP(client.getTargetSystem().toString());
        ics.setInitialized(true);
        ics.setTerminated(client.isTerminated());
        ics.setPendingFutures(pF);
        ics.setTimeoutedFutures(tF);
        ics.setUA(client.getUserAgent());
        ics.setTotalSentMessages(tSM);
        ics.setTotalFailedMessages(tFM);
        ics.setTotalReceivedMessages(tRM);

        totalReceivedMessages += tRM;
        totalFailedMessages += tFM;
        totalSentMessages += tSM;
        timeoutedFutures += tF;
        pendingFutures += pF;

        return ics;
    }
}
