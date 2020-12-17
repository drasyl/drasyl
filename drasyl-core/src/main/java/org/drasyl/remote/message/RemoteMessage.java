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

import com.google.protobuf.MessageLite;
import org.drasyl.crypto.Signable;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;

import java.io.IOException;

/**
 * Describes messages that are used to communicate with remote nodes.
 */
public interface RemoteMessage<T extends MessageLite> extends Signable {
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
    byte getHopCount();

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

    /**
     * @return the public header of this message
     * @throws IOException if the public header cannot be read read
     */
    PublicHeader getPublicHeader() throws IOException;

    /**
     * @return the private header of this message
     * @throws IOException if the private header cannot be read read
     */
    PrivateHeader getPrivateHeader() throws IOException;

    /**
     * @return the body of this message
     * @throws IOException if the body cannot be read read
     */
    T getBody() throws IOException;
}
