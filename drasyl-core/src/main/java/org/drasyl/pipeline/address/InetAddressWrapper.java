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
package org.drasyl.pipeline.address;

import org.drasyl.pipeline.Pipeline;

import java.net.InetAddress;
import java.util.Objects;

/**
 * This class holds an {@link java.net.InetAddress} for the processing inside the {@link Pipeline}
 * as an {@link Address}.
 */
public class InetAddressWrapper implements Address {
    private final InetAddress inetAddress;

    public InetAddressWrapper(final InetAddress inetAddress) {
        this.inetAddress = Objects.requireNonNull(inetAddress);
    }

    public static InetAddressWrapper of(final InetAddress inetAddress) {
        return new InetAddressWrapper(inetAddress);
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    @Override
    public String toString() {
        return "InetAddressWrapper{" +
                "inetAddress=" + inetAddress +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InetAddressWrapper that = (InetAddressWrapper) o;
        return Objects.equals(inetAddress, that.inetAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inetAddress);
    }
}
