/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.server.testutils;

import org.awaitility.Durations;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerException;
import org.drasyl.util.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.with;

public final class TestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(TestHelper.class);
    private static final String DIVIDER = "##################################################";

    /**
     * Print msg to console in color.
     *
     * @param msg   message to print
     * @param color color of the message
     */
    public static void colorizedPrintln(String msg, ANSI_COLOR color) {
        LOG.debug(color.getColor() + DIVIDER + msg + DIVIDER + ANSI_COLOR.COLOR_RESET.getColor());
    }

    /**
     * Print msg to console in color.
     *
     * @param msg   message to print
     * @param color color of the message
     * @param style style of the message
     */
    public static void colorizedPrintln(String msg, ANSI_COLOR color, ANSI_COLOR style) {
        LOG.debug(color.getColor() + style.getColor() + DIVIDER + " " + msg + " " + DIVIDER + ANSI_COLOR.COLOR_RESET.getColor());
    }

    /**
     * Executes the given procedure in a relay server environment. The NodeServer is automatically
     * shutdown after execution.
     *
     * @param procedure the procedure
     * @param config    the relay server config
     */
    public static void giveRelayServerEnv(Runnable procedure,
                                          DrasylNodeConfig config,
                                          NodeServer relay) throws NodeServerException {
        giveRelayServerEnv(procedure, config, relay, true);
    }

    /**
     * Executes the given procedure in a server server environment.
     *
     * @param procedure           the procedure
     * @param config              the server server config
     * @param closeAfterProcedure if the NodeServer should automatically shutdown after execution
     */
    public static void giveRelayServerEnv(Runnable procedure,
                                          DrasylNodeConfig config,
                                          NodeServer server,
                                          boolean closeAfterProcedure) throws NodeServerException {
        TestHelper.waitUntilNetworkAvailable(config.getServerBindPort());
        server.open();

        with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                .until(() -> {
                    return NetworkUtil.alive("127.0.0.1", server.getConfig().getServerBindPort());
                });

        procedure.run();

        if (closeAfterProcedure) {
            server.close();
        }
    }

    /**
     * Waits until the given port is available or the timeout is reached.
     *
     * @param port the port
     */
    public static void waitUntilNetworkAvailable(int port) {
        with().pollInSameThread().await().pollDelay(0, TimeUnit.NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                .until(() -> NetworkUtil.available(port));
    }

    public static Identity random() {
        return Identity.of(Crypto.randomString(5));
    }
}
