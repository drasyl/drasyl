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

package org.drasyl.core.server;

import org.drasyl.core.common.models.SessionUID;
import org.drasyl.core.common.models.IPAddress;
import org.drasyl.core.common.tools.NetworkTool;

import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RelayServerConfig {
    private static final Logger LOG = LoggerFactory.getLogger(RelayServerConfig.class);
    private static final String IDLE_RETRIES = "drasyl.server.idle.retries";
    private static final String IDLE_TIMEOUT = "drasyl.server.idle.timeout";
    private static final String RELAY_FLUSH_BUFFER_SIZE = "drasyl.server.flush_buffer_size";
    private static final String RELAY_SSL_ENABLED = "drasyl.server.ssl.enabled";
    private static final String RELAY_SSL_PROTOCOLS = "drasyl.server.ssl.protocols";
    private static final String RELAY_DEBUGGING = "drasyl.server.debugging";
    private static final String RELAY_MONITORING_WEBSOCKET_PATH = "drasyl.server.monitoring.websocket_path";
    private static final String RELAY_MONITORING_PORT = "drasyl.server.monitoring.port";
    private static final String RELAY_MONITORING_TOKEN = "drasyl.server.monitoring.token";
    private static final String RELAY_MONITORING_ENABLED = "drasyl.server.monitoring.enabled";
    private static final String RELAY_MAX_HANDOFF_TIMEOUT = "drasyl.server.max_handoff_timeout";
    private static final String RELAY_DEFAULT_P2P_TIMEOUT = "drasyl.server.default_p2p_timeout";
    private static final String RELAY_MAX_HANDSHAKE_TIMEOUT = "drasyl.server.max_handshake_timeout";
    private static final String RELAY_DEFAULT_FUTURE_TIMEOUT = "drasyl.server.default_future_timeout";
    private static final String RELAY_MAX_HOP_COUNT = "drasyl.server.max_hop_count";
    private static final String RELAY_MSG_BUCKET_LIMIT = "drasyl.server.msg_bucket_limit";
    private static final String RELAY_CONNECTION_TRIES = "drasyl.server.connection_tries";
    private static final String RELAY_INIT_PEER_LIST = "drasyl.server.init_peer_list";
    private static final String RELAY_ENTRYPOINT = "drasyl.server.entrypoint";
    private static final String RELAY_UID = "drasyl.server.UID";
    private static final String RELAY_DEFAULT_CHANNEL = "drasyl.server.default_channel";
    private static final String RELAY_USER_AGENT = "drasyl.server.user-agent";
    private static final String RELAY_MONITORING_DEAD_CLIENTS_SAVING_TIME = "drasyl.server.monitoring.dead_clients_saving_time";
    private static final String RELAY_CHANNEL_INITIALIZER = "drasyl.server.channel_initializer";
    private static final String RELAY_MAX_CONTENT_LENGTH = "drasyl.server.max_content_length";

    private final String relayMonitoringToken;
    private final List<String> relayInitPeerList;
    private final int relayMonitoringPort;
    private final Config config;
    private final Duration relayMaxHandoffTimeout;
    private final int relayMaxHopCount;
    private final String relayMonitoringWebsocketPath;
    private final boolean relayMonitoringEnabled;
    private final int relayMsgBucketLimit;
    private final boolean relayDebugging;
    private final List<IPAddress> relayInitialPeers;
    private final URI relayEntrypoint;
    private final SessionUID relayUID;
    private final String relayUserAgent;
    private final Duration relayMaxHandshakeTimeout;
    private final Duration relayDefaultFutureTimeout;
    private final Duration relayDefaultP2PTimeout;
    private final int relayConnectionTries;
    private final int idleRetries;
    private final Duration idleTimeout;
    private final boolean relaySslEnabled;
    private final List<String> relaySslProtocols;
    private final int relayFlushBufferSize;
    private final String relayDefaultChannel;
    private final Duration deadClientsSavingTime;
    private final String channelInitializer;
    private final int relayMaxContentLength;

    public RelayServerConfig(Config config) throws URISyntaxException {
        try {
            config.checkValid(ConfigFactory.defaultReference(), "relay");
        } catch (ConfigException e) {
            LOG.error("Can't load configs: ", e);
            System.exit(-1);
        }

        List<IPAddress> initPeerList = new ArrayList<>();
        for (String arg : config.getStringList(RELAY_INIT_PEER_LIST)) {
            try {
                initPeerList.add(new IPAddress(arg));
            } catch (IllegalArgumentException e) {
                LOG.error("IP address " + arg + " is invalid: {}", e);
            }
        }
        this.relayInitialPeers = ImmutableList.copyOf(initPeerList);

        this.relaySslEnabled = config.getBoolean(RELAY_SSL_ENABLED);
        String protocol = relaySslEnabled ? "wss://" : "ws://";

        if (config.hasPath(RELAY_ENTRYPOINT)) {
            this.relayEntrypoint = new URI(config.getString(RELAY_ENTRYPOINT));
        } else {
            this.relayEntrypoint = new URI(protocol + initExternalIP() + ":22527/");
        }

        if (config.hasPath(RELAY_UID)) {
            this.relayUID = SessionUID.of(config.getString(RELAY_UID));
        } else {
            this.relayUID = SessionUID.random();
        }

        this.config = config;

        if (config.hasPath(RELAY_DEBUGGING) && config.getBoolean(RELAY_DEBUGGING)) {
            this.relayDebugging = true;
            System.err.println("Attention DEBUGGING Mode is activated!"); // NOSONAR
        } else {
            this.relayDebugging = false;
        }

        this.channelInitializer = config.getString(RELAY_CHANNEL_INITIALIZER);
        this.relayMaxContentLength = (int) Math.min(config.getMemorySize(RELAY_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);
        this.relayMsgBucketLimit = config.getInt(RELAY_MSG_BUCKET_LIMIT);
        this.relayMaxHandoffTimeout = config.getDuration(RELAY_MAX_HANDOFF_TIMEOUT);
        this.relayMonitoringEnabled = config.getBoolean(RELAY_MONITORING_ENABLED);
        this.relayMonitoringPort = config.getInt(RELAY_MONITORING_PORT);
        this.relayMonitoringToken = config.getString(RELAY_MONITORING_TOKEN);
        this.relayMonitoringWebsocketPath = config.getString(RELAY_MONITORING_WEBSOCKET_PATH);

        this.relayInitPeerList = config.getStringList(RELAY_INIT_PEER_LIST);

        this.relayUserAgent = config.getString(RELAY_USER_AGENT);

        this.relayMaxHopCount = config.getInt(RELAY_MAX_HOP_COUNT);
        this.relayMaxHandshakeTimeout = config.getDuration(RELAY_MAX_HANDSHAKE_TIMEOUT);
        this.relayDefaultFutureTimeout = config.getDuration(RELAY_DEFAULT_FUTURE_TIMEOUT);
        this.relayDefaultP2PTimeout = config.getDuration(RELAY_DEFAULT_P2P_TIMEOUT);
        this.relayConnectionTries = config.getInt(RELAY_CONNECTION_TRIES);
        this.idleRetries = config.getInt(IDLE_RETRIES);
        this.idleTimeout = config.getDuration(IDLE_TIMEOUT);
        this.relaySslProtocols = config.getStringList(RELAY_SSL_PROTOCOLS);
        this.relayFlushBufferSize = config.getInt(RELAY_FLUSH_BUFFER_SIZE);
        this.relayDefaultChannel = config.getString(RELAY_DEFAULT_CHANNEL);
        this.deadClientsSavingTime = config.getDuration(RELAY_MONITORING_DEAD_CLIENTS_SAVING_TIME);
    }

    public int getRelayMonitoringPort() {
        return relayMonitoringPort;
    }

    public int getRelayMsgBucketLimit() {
        return relayMsgBucketLimit;
    }

    public boolean isRelayDebugging() {
        return relayDebugging;
    }

    public Duration getRelayMaxHandoffTimeout() {
        return relayMaxHandoffTimeout;
    }

    @Override
    public String toString() {
        return "RelayServerConfig [config=" + config.getConfig("drasyl.server") + "]";
    }

    public boolean isRelayMonitoringEnabled() {
        return relayMonitoringEnabled;
    }

    public String getRelayMonitoringToken() {
        return relayMonitoringToken;
    }

    public List<String> getRelayInitPeerList() {
        return relayInitPeerList;
    }

    private String initExternalIP() {
        String extIP = "localhost";
        try {
            extIP = NetworkTool.getExternalIPAddress();
        } catch (IOException e) {
            LOG.error("External IP request unsuccessful: ", e);
        }

        return extIP;
    }

    public String getRelayUserAgent() {
        return relayUserAgent;
    }

    public SessionUID getRelayUID() {
        return relayUID;
    }

    public URI getRelayEntrypoint() {
        return relayEntrypoint;
    }

    public List<IPAddress> getRelayInitialPeers() {
        return relayInitialPeers;
    }

    public Duration getRelayMaxHandshakeTimeout() {
        return relayMaxHandshakeTimeout;
    }

    public Duration getRelayDefaultFutureTimeout() {
        return relayDefaultFutureTimeout;
    }

    public Duration getRelayDefaultP2PTimeout() {
        return relayDefaultP2PTimeout;
    }

    public int getRelayConnectionTries() {
        return relayConnectionTries;
    }

    public int getIdleRetries() {
        return idleRetries;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public boolean isRelaySslEnabled() {
        return relaySslEnabled;
    }

    public String[] getRelaySslProtocols() {
        return relaySslProtocols.toArray(new String[0]);
    }

    public int getRelayFlushBufferSize() {
        return relayFlushBufferSize;
    }

    public String getRelayDefaultChannel() {
        return relayDefaultChannel;
    }

    public String getRelayMonitoringWebsocketPath() {
        return relayMonitoringWebsocketPath;
    }

    public int getRelayMaxHopCount() {
        return relayMaxHopCount;
    }

    public Duration getDeadClientsSavingTime() {
        return deadClientsSavingTime;
    }

    public String getChannelInitializer() {
        return channelInitializer;
    }

    public int getRelayMaxContentLength() {
        return relayMaxContentLength;
    }
}
