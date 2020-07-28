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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.peer.PeerInformation;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing the welcome message of the node server, including fallback information..
 */
public class WelcomeMessage extends AbstractMessageWithUserAgent implements ResponseMessage<JoinMessage> {
    private final PeerInformation peerInformation;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final MessageId correspondingId;

    @JsonCreator
    private WelcomeMessage(@JsonProperty("id") MessageId id,
                           @JsonProperty("userAgent") String userAgent,
                           @JsonProperty("peerInformation") PeerInformation peerInformation,
                           @JsonProperty("correspondingId") MessageId correspondingId) {
        super(id, userAgent);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    /**
     * Creates new welcome message.
     *
     * @param peerInformation the peer information of the node server
     * @param correspondingId the corresponding id of the previous join message
     */
    public WelcomeMessage(PeerInformation peerInformation,
                          MessageId correspondingId) {
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    public PeerInformation getPeerInformation() {
        return this.peerInformation;
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), peerInformation, correspondingId);
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
        WelcomeMessage that = (WelcomeMessage) o;
        return Objects.equals(peerInformation, that.peerInformation) &&
                Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public String toString() {
        return "WelcomeMessage{" +
                "peerInformation=" + peerInformation +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id +
                '}';
    }
}
