/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.messages;

import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.SessionUID;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message containing the address of the relay server that is responsible for
 * a client.
 */
public class ResponsiblePeer extends AbstractMessage {
    @JsonProperty("host")
    private final IPAddress ipAddress;
    private final Join join;

    ResponsiblePeer(IPAddress ipAddress, Join join) {
        this.ipAddress = requireNonNull(ipAddress);
        this.join = requireNonNull(join);
    }

    ResponsiblePeer() {
        ipAddress = null;
        join = null;
    }

    /**
     * Creates a message containing the address of the responsible peer.
     *
     * @param ipAddress the IP address of the responsible peer
     */
    public ResponsiblePeer(IPAddress ipAddress) {
        this.ipAddress = requireNonNull(ipAddress);
        this.join = null;
    }

    /**
     * Creates a message containing the address of the responsible peer.
     *
     * @param ipAddress the IP address of the responsible peer
     * @param clientUID the session UID of the client
     * @param relayUID  the session UID of the old relay server
     */
    public ResponsiblePeer(IPAddress ipAddress, SessionUID clientUID, SessionUID relayUID) {
        this.ipAddress = requireNonNull(ipAddress);
        this.join = new Join(requireNonNull(clientUID), requireNonNull(relayUID),
                requireNonNull(getMessageID()));
    }


    /**
     * @return the IP address
     */
    public IPAddress getHost() {
        return ipAddress;
    }

    /**
     * @return the join message that should be sent to the new responsible peer by
     *         the client
     */
    public Join getJoin() {
        return join;
    }

    @Override
    public String toString() {
        return "ResponsiblePeer [ipAddress=" + ipAddress + ", join=" + join + ", messageID=" + getMessageID() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResponsiblePeer)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ResponsiblePeer that = (ResponsiblePeer) o;
        return Objects.equals(ipAddress, that.ipAddress) &&
                Objects.equals(getJoin(), that.getJoin());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ipAddress, getJoin());
    }
}
