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
package org.drasyl.cli.tun;

import org.drasyl.crypto.Hashing;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.network.Subnet;

import java.net.InetAddress;

import static java.util.Objects.requireNonNull;

/**
 * Mapping between {@link DrasylAddress} and {@link InetAddress}.
 */
public class TunRoute {
    private final DrasylAddress overlayAddress;
    private final InetAddress inetAddress;

    public TunRoute(final DrasylAddress overlayAddress, final InetAddress inetAddress) {
        this.overlayAddress = requireNonNull(overlayAddress);
        this.inetAddress = inetAddress;
    }

    public TunRoute(final DrasylAddress overlayAddress) {
        this(overlayAddress, null);
    }

    public DrasylAddress overlayAddress() {
        return overlayAddress;
    }

    public InetAddress inetAddress() {
        return inetAddress;
    }

    public TunRoute ensureInetAddress(final Subnet subnet) {
        if (inetAddress != null) {
            return this;
        }
        else {
            return new TunRoute(overlayAddress, deriveInetAddressFromOverlayAddress(subnet, overlayAddress));
        }
    }

    static InetAddress deriveInetAddressFromOverlayAddress(final Subnet subnet,
                                                           final DrasylAddress overlayAddress) {
        final long identityHash = UnsignedInteger.of(Hashing.murmur3x32(overlayAddress.toByteArray())).getValue();
        final int n = (int) (identityHash % subnet.networkSize());
        return subnet.nth(n);
    }
}
