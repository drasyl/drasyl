/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.drasyl.util.network;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;

/**
 * Source: <a href="https://github.com/openjdk/jdk/blob/b953386de1b871653b1b4ac1999f0d2fca72dfc4/src/java.base/macosx/classes/java/net/DefaultInterface.java">OpenJDK</a>
 * <p>
 * Choose a network interface to be the default for outgoing IPv6 traffic that does not specify a
 * scope_id (and which needs one). We choose the first interface that is up and is (in order of
 * preference): 1. neither loopback nor point to point 2. point to point 3. loopback 4. none.
 * Platforms that do not require a default interface implement a dummy that returns null.
 */
@SuppressWarnings({
        "java:S108",
        "java:S134",
        "java:S138",
        "java:S1066",
        "java:S1118",
        "java:S1166",
        "java:S1142",
        "java:S1541",
        "java:S1659",
        "java:S1700",
        "java:S3776"
        , "CatchMayIgnoreException"
})
class DefaultInterface {
    private static final NetworkInterface defaultInterface =
            chooseDefaultInterface();

    static NetworkInterface getDefault() {
        return defaultInterface;
    }

    /**
     * Choose a default interface. This method returns the first interface that is both "up" and
     * supports multicast. This method chooses an interface in order of preference, using the
     * following algorithm:
     *
     * <pre>
     * Interfaces that are down, or don't support multicasting, are skipped.
     * In steps 1-4 below, PPP and loopback interfaces are skipped.
     *
     * 1. The first interface that has at least an IPv4 address, and an IPv6 address,
     *    and a non link-local IP address, is picked.
     *
     * 2. If none is found, then the first interface that has at least an
     *    IPv4 address, and an IPv6 address is picked.
     *
     * 3. If none is found, then the first interface that has at least a
     *    non link local IP address is picked.
     *
     * 4. If none is found, then the first non loopback and non PPP interface
     *    is picked.
     *
     * 5. If none is found then first PPP interface is picked.
     *
     * 6. If none is found, then the first loopback interface is picked.
     *
     * 7. If none is found, then null is returned.
     * </pre>
     *
     * @return the chosen interface or {@code null} if there isn't a suitable default
     */
    private static NetworkInterface chooseDefaultInterface() {
        final Enumeration<NetworkInterface> nifs;

        try {
            nifs = NetworkInterface.getNetworkInterfaces();
        }
        catch (final IOException ignore) {
            // unable to enumerate network interfaces
            return null;
        }

        NetworkInterface preferred = null;
        NetworkInterface dual = null;
        NetworkInterface nonLinkLocal = null;
        NetworkInterface ppp = null;
        NetworkInterface loopback = null;

        while (nifs.hasMoreElements()) {
            final NetworkInterface ni = nifs.nextElement();
            try {
                if (!ni.isUp() || !ni.supportsMulticast()) {
                    continue;
                }

                boolean ip4 = false, ip6 = false, isNonLinkLocal = false;
                final PrivilegedAction<Enumeration<InetAddress>> pa = ni::getInetAddresses;
                final Enumeration<InetAddress> addrs = AccessController.doPrivileged(pa);
                while (addrs.hasMoreElements()) {
                    final InetAddress addr = addrs.nextElement();
                    if (!addr.isAnyLocalAddress()) {
                        if (addr instanceof Inet4Address) {
                            ip4 = true;
                        }
                        else if (addr instanceof Inet6Address) {
                            ip6 = true;
                        }
                        if (!addr.isLinkLocalAddress()) {
                            isNonLinkLocal = true;
                        }
                    }
                }

                final boolean isLoopback = ni.isLoopback();
                final boolean isPPP = ni.isPointToPoint();
                if (!isLoopback && !isPPP) {
                    // found an interface that is not the loopback or a
                    // point-to-point interface
                    if (preferred == null) {
                        preferred = ni;
                    }
                    if (ip4 && ip6) {
                        if (isNonLinkLocal) {
                            return ni;
                        }
                        if (dual == null) {
                            dual = ni;
                        }
                    }
                    if (nonLinkLocal == null) {
                        if (isNonLinkLocal) {
                            nonLinkLocal = ni;
                        }
                    }
                }
                if (ppp == null && isPPP) {
                    ppp = ni;
                }
                if (loopback == null && isLoopback) {
                    loopback = ni;
                }
            }
            catch (final IOException skip) {
            }
        }

        if (dual != null) {
            return dual;
        }
        else if (nonLinkLocal != null) {
            return nonLinkLocal;
        }
        else if (preferred != null) {
            return preferred;
        }
        else {
            return (ppp != null) ? ppp : loopback;
        }
    }
}
