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
package org.drasyl.handler.remote.protocol;

import io.netty.buffer.ByteBufAllocator;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

/**
 * Describes a message whose content has been read completely. This is the case for unencrypted or
 * decrypted messages.
 *
 * @see PartialReadMessage
 */
public interface FullReadMessage<T extends FullReadMessage<?>> extends RemoteMessage {
    /**
     * Returns the {@link IdentityPublicKey} of the message recipient.
     *
     * @return the {@link IdentityPublicKey} of the message recipient
     */
    DrasylAddress getRecipient();

    /**
     * Returns this message with incremented hop count.
     *
     * @return this message with incremented hop count.
     * @throws IllegalStateException if hop count overflows
     */
    T incrementHopCount();

    /**
     * Returns an armed version ({@link ArmedProtocolMessage}) of this message for sending it
     * through untrustworthy channels.
     *
     * @param alloc
     * @param cryptoInstance the crypto instance that should be used
     * @param sessionPair    will be used for encryption
     * @return the armed version of this message
     * @throws InvalidMessageFormatException if arming was not possible
     */
    ArmedProtocolMessage arm(ByteBufAllocator alloc,
                             Crypto cryptoInstance,
                             SessionPair sessionPair) throws InvalidMessageFormatException;
}
