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
package org.drasyl.pipeline.address;

import org.drasyl.pipeline.Pipeline;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A {@link InetSocketAddress} that can be used as an {@link Address} in the {@link Pipeline}.
 */
@SuppressWarnings("java:S4926")
public class InetSocketAddressWrapper extends InetSocketAddress implements Address {
    private static final long serialVersionUID = -453196965326684876L;

    /**
     * Creates a socket address where the IP address is the wildcard address and the port number a
     * specified value.
     * <p>
     * A valid port value is between 0 and 65535. A port number of {@code zero} will let the system
     * pick up an ephemeral port in a {@code bind} operation.
     *
     * @param port The port number
     * @throws IllegalArgumentException if the port parameter is outside the specified range of
     *                                  valid port values.
     */
    public InetSocketAddressWrapper(final int port) {
        super(port);
    }

    /**
     * Creates a socket address from an IP address and a port number.
     * <p>
     * A valid port value is between 0 and 65535. A port number of {@code zero} will let the system
     * pick up an ephemeral port in a {@code bind} operation.
     * <p>
     * A {@code null} address will assign the <i>wildcard</i> address.
     *
     * @param addr The IP address
     * @param port The port number
     * @throws IllegalArgumentException if the port parameter is outside the specified range of
     *                                  valid port values.
     */
    public InetSocketAddressWrapper(final InetAddress addr, final int port) {
        super(addr, port);
    }

    /**
     * Creates a socket address from a hostname and a port number.
     * <p>
     * An attempt will be made to resolve the hostname into an InetAddress. If that attempt fails,
     * the address will be flagged as <I>unresolved</I>.
     * <p>
     * If there is a security manager, its {@code checkConnect} method is called with the host name
     * as its argument to check the permission to resolve it. This could result in a
     * SecurityException.
     * <p>
     * A valid port value is between 0 and 65535. A port number of {@code zero} will let the system
     * pick up an ephemeral port in a {@code bind} operation.
     *
     * @param hostname the Host name
     * @param port     The port number
     * @throws IllegalArgumentException if the port parameter is outside the range of valid port
     *                                  values, or if the hostname parameter is {@code null}.
     * @throws SecurityException        if a security manager is present and permission to resolve
     *                                  the host name is denied.
     * @see #isUnresolved()
     */
    public InetSocketAddressWrapper(final String hostname, final int port) {
        super(hostname, port);
    }

    /**
     * Converts a {@code address} to an {@link InetSocketAddress}
     *
     * @param address address to be converted
     * @throws IllegalArgumentException if the port of the converted address is outside the
     *                                  specified range of valid port values.
     */
    public InetSocketAddressWrapper(final InetSocketAddress address) {
        super(address.getAddress(), address.getPort());
    }
}
