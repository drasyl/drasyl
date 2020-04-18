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

import city.sane.relay.common.models.SessionChannel;
import city.sane.relay.common.models.SessionUID;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A message representing a join to the relay server. This message allows a) to
 * set the client UID b) to join one or more channels
 * <p>
 * The relay server forwards messages from the client only to clients that are
 * in the same subscribed channels.
 */
public class Join extends UserAgentMessage implements UnrestrictedPassableMessage {
    private final SessionUID clientUID;
    private final Set<SessionChannel> sessionChannels;
    private final SessionUID relayUID;
    private final String rpmID;

    protected Join() {
        clientUID = null;
        sessionChannels = null;
        relayUID = null;
        rpmID = null;
    }

    /**
     * Creates a new join message.
     *
     * @param clientUID       the session UID of the client
     * @param sessionChannels the list of channels the client wants to join
     * @param relayUID        the session UID of the old relay server
     * @param rpmID           the ID of the {@link ResponsiblePeer}
     */
    private Join(SessionUID clientUID, Set<SessionChannel> sessionChannels, SessionUID relayUID, String rpmID) {
        this.clientUID = Objects.requireNonNull(clientUID);
        this.sessionChannels = Objects.requireNonNull(sessionChannels);
        this.relayUID = relayUID;
        this.rpmID = rpmID;

        if (clientUID.getUIDs().size() > 1)
            throw new IllegalArgumentException("The client uid can't be a multicast address.");
    }

    /**
     * Creates a new join message.
     *
     * @param clientUID       the session UID of the client
     * @param sessionChannels the list of channels the client wants to join
     */
    public Join(SessionUID clientUID, Set<SessionChannel> sessionChannels) {
        this(clientUID, sessionChannels, null, null);
    }

    /**
     * Creates a new join message.
     *
     * @param clientUID the session UID of the client
     * @param relayUID  the UID of the old relay server
     * @param rpmID     the ID of the {@link ResponsiblePeer}
     */
    public Join(SessionUID clientUID, SessionUID relayUID, String rpmID) {
        this(clientUID, new HashSet<>(), relayUID, rpmID);
    }

    /**
     * @return the session UID of the client
     */
    public SessionUID getClientUID() {
        return clientUID;
    }

    /**
     * @return the channels
     */
    public Set<SessionChannel> getSessionChannels() {
        return sessionChannels;
    }

    /**
     * @return the old relay server UID
     */
    public SessionUID getRelayUID() {
        return relayUID;
    }

    /**
     * @return the rpm ID
     */
    public String getRpmID() {
        return rpmID;
    }

    @Override
    public String toString() {
        return "Join [clientUID=" + clientUID + ", channels=" + sessionChannels + ", relayUID=" + relayUID + ", rpmID="
                + rpmID + ", messageID=" + getMessageID() + ", User-Agent=" + getUserAgent() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Join)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Join that = (Join) o;
        return Objects.equals(getClientUID(), that.getClientUID()) &&
                Objects.equals(getSessionChannels(), that.getSessionChannels()) &&
                Objects.equals(getRelayUID(), that.getRelayUID()) &&
                Objects.equals(getRpmID(), that.getRpmID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getClientUID(), getSessionChannels(), getRelayUID(), getRpmID());
    }
}
