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

package city.sane.relay.server.testutils;

import static org.awaitility.Awaitility.with;

import java.util.concurrent.TimeUnit;

import city.sane.relay.server.RelayServerConfig;
import city.sane.relay.server.RelayServerException;
import org.awaitility.Durations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import city.sane.relay.common.tools.NetworkTool;
import city.sane.relay.common.util.function.Procedure;
import city.sane.relay.server.RelayServer;

public final class TestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(TestHelper.class);
    private static final String DIVIDER = "##################################################";

    /**
     * Print msg to console in color.
     * 
     * @param msg   message to print
     * @param color color of the message
     */
    public static void println(String msg, ANSI_COLOR color) {
        LOG.debug(color.getColor() + DIVIDER + msg + DIVIDER + ANSI_COLOR.RESET.getColor());
    }

    /**
     * Print msg to console in color.
     * 
     * @param msg   message to print
     * @param color color of the message
     * @param style style of the message
     */
    public static void println(String msg, ANSI_COLOR color, ANSI_COLOR style) {
        LOG.debug(color.getColor() + style.getColor() + DIVIDER + msg + DIVIDER + ANSI_COLOR.RESET.getColor());
    }

    /**
     * Waits until the given port is available or the timeout is reached.
     * 
     * @param port the port
     */
    public static void waitUntilNetworkAvailable(int port) {
        with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                .until(() -> {
                    return NetworkTool.available(port);
                });
    }

    /**
     * Executes the given procedure in a relay server environment. The RelayServer
     * is automatically shutdown after execution.
     * 
     * @param procedure the procedure
     * @param config    the relay server config
     */
    public static void giveRelayServerEnv(Procedure procedure, RelayServerConfig config, RelayServer relay) throws RelayServerException {
        giveRelayServerEnv(procedure, config, relay, true);
    }

    /**
     * Executes the given procedure in a relay server environment.
     * 
     * @param procedure           the procedure
     * @param config              the relay server config
     * @param closeAfterProcedure if the RelayServer should automatically shutdown
     *                            after execution
     */
    public static void giveRelayServerEnv(Procedure procedure, RelayServerConfig config, RelayServer relay,
            boolean closeAfterProcedure) throws RelayServerException {
        TestHelper.waitUntilNetworkAvailable(config.getRelayEntrypoint().getPort());
        relay.open();

        RelayServer.startMonitoringServer(relay);

        with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                .until(() -> {
                    return NetworkTool.alive("127.0.0.1", relay.getConfig().getRelayEntrypoint().getPort());
                });

        procedure.execute();

        if (closeAfterProcedure)
            relay.close();
    }
}
