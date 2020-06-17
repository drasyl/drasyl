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
package testutils;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.util.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static testutils.AnsiColor.COLOR_RESET;

public final class TestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(TestHelper.class);
    private static final String DIVIDER = "##################################################";

    /**
     * Print msg to console in color.
     *
     * @param msg   message to print
     * @param color color of the message
     */
    public static void colorizedPrintln(String msg, AnsiColor color) {
        LOG.debug(color.getColor() + DIVIDER + msg + DIVIDER + COLOR_RESET.getColor());
    }

    /**
     * Print msg to console in color.
     *
     * @param msg   message to print
     * @param color color of the message
     * @param style style of the message
     */
    public static void colorizedPrintln(String msg, AnsiColor color, AnsiColor style) {
        LOG.debug(color.getColor() + style.getColor() + DIVIDER + " " + msg + " " + DIVIDER + COLOR_RESET.getColor());
    }

    /**
     * Waits until the given port is available or the timeout is reached.
     *
     * @param port the port
     */
    public static void waitUntilNetworkAvailable(int port) {
        Awaitility.with().pollInSameThread().await().pollDelay(0, NANOSECONDS).atMost(Durations.FIVE_MINUTES)
                .until(() -> NetworkUtil.available(port));
    }

    public static Identity random() {
        try {
            return Identity.of(CompressedPublicKey.of(Crypto.generateKeys().getPublic()));
        }
        catch (CryptoException e) {
            return null;
        }
    }
}
