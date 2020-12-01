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
package org.drasyl.remote.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.drasyl.crypto.Signable;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

/**
 * Describes messages that are used to communicate with remote nodes.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AcknowledgementMessage.class),
        @JsonSubTypes.Type(value = RemoteApplicationMessage.class),
        @JsonSubTypes.Type(value = DiscoverMessage.class),
        @JsonSubTypes.Type(value = UniteMessage.class),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface RemoteMessage extends Signable {
    /**
     * Returns the unique id of this message. Each message generates a random id when it is
     * created.
     *
     * @return the unique id of this message. Each message generates a random id when it is created.
     */
    MessageId getId();

    /**
     * Returns the user agent of the sender's node.
     *
     * @return the user agent of the sender's node.
     */
    UserAgent getUserAgent();

    /**
     * Returns the network the sender belongs to.
     *
     * @return the network the sender belongs to
     */
    int getNetworkId();

    /**
     * Returns this message's sender.
     *
     * @return this message's sender.
     */
    CompressedPublicKey getSender();

    /**
     * Returns this message sender's proof of work.
     *
     * @return this message sender's proof of work.
     */
    ProofOfWork getProofOfWork();

    /**
     * Returns this message's recipient.
     *
     * @return this message's recipient.
     */
    CompressedPublicKey getRecipient();

    /**
     * Returns this message's hop count. Starts at 0 and is incremented every time it is sent. Once
     * the message reaches the limit defined in config {@code drasyl.message.hop-limit} it will be
     * dropped.
     *
     * @return this message's hop count.
     */
    short getHopCount();

    /**
     * Increases the message's hop count.
     */
    void incrementHopCount();

    /**
     * Returns this message's signature.
     *
     * @return this message's signature.
     */
    Signature getSignature();
}
