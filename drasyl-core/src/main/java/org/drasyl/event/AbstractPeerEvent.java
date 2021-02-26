/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.event;

import org.drasyl.annotation.NonNull;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

abstract class AbstractPeerEvent implements PeerEvent {
    protected final Peer peer;

    /**
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    protected AbstractPeerEvent(final Peer peer) {
        this.peer = requireNonNull(peer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peer);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractPeerEvent that = (AbstractPeerEvent) o;
        return Objects.equals(peer, that.peer);
    }

    @NonNull
    @Override
    public Peer getPeer() {
        return peer;
    }
}
