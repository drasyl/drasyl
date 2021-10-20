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

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.UnsignedShort;

import static org.drasyl.handler.remote.protocol.Nonce.randomNonce;
import static org.drasyl.handler.remote.protocol.PrivateHeader.MessageType.ACKNOWLEDGEMENT;

/**
 * Acknowledges a {@link DiscoveryMessage}.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class AcknowledgementMessage extends AbstractFullReadMessage<AcknowledgementMessage> {
    public static final int LENGTH = 8;

    /**
     * Returns the {@link DiscoveryMessage#getTime()} value of the corresponding {@link
     * DiscoveryMessage}.
     */
    public abstract long getTime();

    @Override
    public AcknowledgementMessage incrementHopCount() {
        return AcknowledgementMessage.of(getHopCount().increment(), getArmed(), getNetworkId(), getNonce(), getRecipient(), getSender(), getProofOfWork(), getTime());
    }

    @Override
    protected void writePrivateHeaderTo(final ByteBuf out) {
        PrivateHeader.of(ACKNOWLEDGEMENT, UnsignedShort.of(LENGTH)).writeTo(out);
    }

    @Override
    protected void writeBodyTo(final ByteBuf out) {
        out.writeLong(getTime());
    }

    /**
     * Creates new acknowledgement message.
     *
     * @param hopCount    the hop count
     * @param isArmed
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, or {@code correspondingId} is
     *                              {@code null}
     */
    @SuppressWarnings("java:S107")
    public static AcknowledgementMessage of(final HopCount hopCount,
                                            final boolean isArmed,
                                            final int networkId,
                                            final Nonce nonce,
                                            final DrasylAddress recipient,
                                            final DrasylAddress sender,
                                            final ProofOfWork proofOfWork,
                                            final long time) {
        return new AutoValue_AcknowledgementMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                isArmed,
                recipient,
                time
        );
    }

    /**
     * Creates new acknowledgement message with random {@link Nonce}, and minimal {@link HopCount}.
     *
     * @param networkId   the network id
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, {@code recipient}, or
     *                              {@code correspondingId} is {@code null}
     */
    public static AcknowledgementMessage of(final int networkId,
                                            final DrasylAddress recipient,
                                            final IdentityPublicKey sender,
                                            final ProofOfWork proofOfWork,
                                            final long time) {
        return of(
                HopCount.of(),
                false,
                networkId,
                randomNonce(),
                recipient,
                sender,
                proofOfWork,
                time
        );
    }

    /**
     * Creates new acknowledgement message.
     *
     * @param hopCount    the hop count
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, {@code body}, or {@code
     *                              body.getCorrespondingId()} is {@code null}
     */
    @SuppressWarnings("java:S107")
    static AcknowledgementMessage of(final HopCount hopCount,
                                     final int networkId,
                                     final Nonce nonce,
                                     final DrasylAddress recipient,
                                     final DrasylAddress sender,
                                     final ProofOfWork proofOfWork,
                                     final ByteBuf body) throws InvalidMessageFormatException {
        if (body.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("AcknowledgementMessage requires " + LENGTH + " readable bytes. Only " + body.readableBytes() + " left.");
        }

        return of(
                hopCount, false, networkId, nonce,
                recipient, sender,
                proofOfWork,
                body.readLong());
    }
}
