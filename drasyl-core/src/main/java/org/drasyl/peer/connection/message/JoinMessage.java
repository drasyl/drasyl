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
package org.drasyl.peer.connection.message;

import com.google.common.collect.ImmutableSet;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.drasyl.util.KeyValue;

import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a join to the node server.
 */
public class JoinMessage extends AbstractMessageWithUserAgent implements RequestMessage {
    private final Identity identity;
    private final PeerInformation peerInformation;
    private final Set<KeyValue<Identity, PeerInformation>> childrenAndGrandchildren;

    protected JoinMessage() {
        identity = null;
        peerInformation = null;
        childrenAndGrandchildren = null;
    }

    /**
     * Creates a new join message.
     *
     * @param identity        the identity of the joining node
     * @param peerInformation the endpoints of the joining node
     */
    public JoinMessage(Identity identity,
                       PeerInformation peerInformation,
                       Set<KeyValue<Identity, PeerInformation>> childrenAndGrandchildren) {
        this.identity = requireNonNull(identity);
        this.peerInformation = requireNonNull(peerInformation);
        this.childrenAndGrandchildren = requireNonNull(childrenAndGrandchildren);
    }

    public Set<KeyValue<Identity, PeerInformation>> getChildrenAndGrandchildren() {
        return ImmutableSet.copyOf(this.childrenAndGrandchildren);
    }

    public PeerInformation getPeerInformation() {
        return this.peerInformation;
    }

    public Identity getIdentity() {
        return this.identity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), identity, peerInformation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        JoinMessage join = (JoinMessage) o;
        return Objects.equals(identity, join.identity) &&
                Objects.equals(peerInformation, join.peerInformation) &&
                Objects.equals(childrenAndGrandchildren, join.childrenAndGrandchildren);
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "peerInformation=" + peerInformation +
                ", childrenAndGrandchildren=" + childrenAndGrandchildren +
                ", id='" + id + '\'' +
                ", identity=" + identity +
                '}';
    }
}
