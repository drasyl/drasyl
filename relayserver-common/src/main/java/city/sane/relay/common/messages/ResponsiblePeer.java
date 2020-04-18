/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.common.messages;

import city.sane.relay.common.models.SessionUID;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message containing the address of the relay server that is responsible for
 * a client.
 */
public class ResponsiblePeer extends AbstractMessage {
    @JsonProperty("host")
    private final URI host;
    private final Join join;

    ResponsiblePeer(URI uri, Join join) {
        this.host = requireNonNull(uri);
        this.join = requireNonNull(join);
    }

    ResponsiblePeer() {
        host = null;
        join = null;
    }

    /**
     * Creates a message containing the address of the responsible peer.
     *
     * @param uri the IP address of the responsible peer
     */
    public ResponsiblePeer(URI uri) {
        this.host = requireNonNull(uri);
        this.join = null;
    }

    /**
     * Creates a message containing the address of the responsible peer.
     *
     * @param uri the IP address of the responsible peer
     * @param clientUID the session UID of the client
     * @param relayUID  the session UID of the old relay server
     */
    public ResponsiblePeer(URI uri, SessionUID clientUID, SessionUID relayUID) {
        this.host = requireNonNull(uri);
        this.join = new Join(requireNonNull(clientUID), requireNonNull(relayUID),
                requireNonNull(getMessageID()));
    }

    /**
     * @return the IP address
     */
    public URI getHost() {
        return host;
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
        return "ResponsiblePeer [ipAddress=" + host + ", join=" + join + ", messageID=" + getMessageID() + "]";
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
        return Objects.equals(host, that.host) &&
                Objects.equals(getJoin(), that.getJoin());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), host, getJoin());
    }
}
