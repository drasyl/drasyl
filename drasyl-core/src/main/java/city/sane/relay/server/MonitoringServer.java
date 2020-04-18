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

package city.sane.relay.server;

import city.sane.relay.server.monitoring.Aggregator;
import city.sane.relay.server.monitoring.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitoringServer {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringServer.class);

    private final WebSocketServer webSocketServer;

    public MonitoringServer(RelayServer relay) {
        RelayServerConfig config = relay.getConfig();

        webSocketServer = new WebSocketServer(config.getRelayMonitoringPort(),
                config.isRelayDebugging(), new Aggregator(relay), config.getRelayMonitoringToken(),
                config.getRelayMonitoringWebsocketPath());
    }

    public void start() throws InterruptedException {
        LOG.info("Start Monitoring Server");
        webSocketServer.start();
    }

    public void stop() {
        LOG.info("Stop Monitoring Server");
        webSocketServer.stop();
    }
}
