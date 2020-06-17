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

import org.drasyl.identity.Identity;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This message is used to request information (like public key and endpoints) for a specific
 * identity.
 */
public class WhoisMessage extends AbstractMessage implements RequestMessage {
    private final Identity requester;
    private final Identity identity;

    WhoisMessage() {
        requester = null;
        identity = null;
    }

    public WhoisMessage(Identity requester, Identity identity) {
        this.requester = requireNonNull(requester);
        this.identity = requireNonNull(identity);
    }

    public Identity getRequester() {
        return requester;
    }

    public Identity getIdentity() {
        return identity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), requester, identity);
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
        WhoisMessage that = (WhoisMessage) o;
        return Objects.equals(requester, that.requester) &&
                Objects.equals(identity, that.identity);
    }

    @Override
    public String toString() {
        return "WhoisMessage{" +
                "requester=" + requester +
                ", identity=" + identity +
                ", id='" + id + '\'' +
                '}';
    }
}
