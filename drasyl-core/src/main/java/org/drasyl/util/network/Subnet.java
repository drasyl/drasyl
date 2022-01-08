/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.util.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Represents an IP range..
 */
public class Subnet {
    private final InetAddress address;
    private final short netmaskLength;
    private final int netmask;
    private final int network;
    private final int broadcast;

    /**
     * @param subnet subnet in CIDR notation
     */
    @SuppressWarnings("java:S109")
    public Subnet(final String subnet) throws UnknownHostException {
        final String[] parts = subnet.split("/");
        address = InetAddress.getByName(parts[0]);
        if (!(address instanceof Inet4Address)) {
            throw new IllegalArgumentException("Currently only IPv4 addresses are supported (sadly!)");
        }
        if (parts.length < 2) {
            netmaskLength = 0;
        }
        else {
            netmaskLength = Short.parseShort(parts[1]);
        }
        final int addressInt = ByteBuffer.wrap(address.getAddress()).getInt();
        final int trailingZeroes = 32 - netmaskLength;
        netmask = (int) (0x0_FFFF_FFFFL << trailingZeroes);
        network = addressInt & netmask;
        broadcast = network | ~netmask;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public String toString() {
        return address.getHostAddress() + "/" + netmaskLength;
    }

    public InetAddress address() {
        return address;
    }

    public int netmaskLength() {
        return netmaskLength;
    }

    public InetAddress network() {
        try {
            return InetAddress.getByAddress(ByteBuffer.allocate(Integer.BYTES).putInt(network).array());
        }
        catch (final UnknownHostException e) {
            // this code should never be reached
            throw new IllegalStateException(e);
        }
    }

    public InetAddress netmask() {
        try {
            return InetAddress.getByAddress(ByteBuffer.allocate(Integer.BYTES).putInt(netmask).array());
        }
        catch (final UnknownHostException e) {
            // this code should never be reached
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the size of this subnetwork.
     */
    public int networkSize() {
        return broadcast - network - 1;
    }

    /**
     * Returns the {@code n}-th {@link InetAddress} of this subnetwork.
     */
    public InetAddress nth(final int n) {
        if (n < 0 || n >= networkSize()) {
            throw new IllegalArgumentException("n must be in range of [0, " + networkSize() + "), but was " + n);
        }
        try {
            return InetAddress.getByAddress(ByteBuffer.allocate(Integer.BYTES).putInt(network + 1 + n).array());
        }
        catch (final UnknownHostException e) {
            // this code should never be reached
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the first {@link InetAddress} of this subnetwork.
     */
    public InetAddress first() {
        try {
            return InetAddress.getByAddress(ByteBuffer.allocate(Integer.BYTES).putInt(network + 1).array());
        }
        catch (final UnknownHostException e) {
            // this code should never be reached
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the last {@link InetAddress} of this subnetwork.
     */
    public InetAddress last() {
        try {
            return InetAddress.getByAddress(ByteBuffer.allocate(Integer.BYTES).putInt(broadcast - 1).array());
        }
        catch (final UnknownHostException e) {
            // this code should never be reached
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns {@code true} if {@code address} is part of this subnet.
     */
    public boolean contains(final InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }

        final int addressInt = ByteBuffer.wrap(address.getAddress()).getInt();
        return network < addressInt && addressInt < broadcast;
    }
}
