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
package org.drasyl.handler.discovery;

import com.google.auto.value.AutoValue;
import org.drasyl.annotation.Nullable;
import org.drasyl.identity.DrasylAddress;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Signals that a direct routing path has been discovered to {@link AddPathAndSuperPeerEvent#getAddress()}
 * and that we are registered as a children as this peer.
 */
@SuppressWarnings({ "java:S118", "java:S1118", "java:S2974" })
@AutoValue
public abstract class AddPathAndSuperPeerEvent implements PathEvent {
    @Nullable
    public abstract InetSocketAddress getInetAddress();

    public abstract long getRtt();

    @Override
    public int hashCode() {
        return Objects.hash(getAddress(), getPath(), getInetAddress());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AddPathAndSuperPeerEvent that = (AddPathAndSuperPeerEvent) o;
        return Objects.equals(getAddress(), that.getAddress()) &&
                Objects.equals(getPath(), that.getPath()) &&
                Objects.equals(getInetAddress(), that.getInetAddress());
    }

    public static AddPathAndSuperPeerEvent of(final DrasylAddress address,
                                              final InetSocketAddress inetAddress,
                                              final Object path,
                                              final long rtt) {
        return new AutoValue_AddPathAndSuperPeerEvent(address, path, inetAddress, rtt);
    }
}
