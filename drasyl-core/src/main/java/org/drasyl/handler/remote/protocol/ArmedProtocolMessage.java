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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.DrasylSodiumWrapper;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.InputStreamHelper;
import org.drasyl.util.UnsignedShort;

import java.io.IOException;

/**
 * Describes a protocol message whose contents has been armed by using authenticated encryption with
 * associated data.
 * <p>
 * Only the message recipient can decrypt and authenticate the message by calling {@link
 * #disarm(Crypto, SessionPair)}.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class ArmedProtocolMessage implements PartialReadMessage {
    // authentication header is 16 bytes long
    public static final int ARMED_HEADER_LENGTH = 16;

    @Override
    public void close() {
        release();
    }

    @Override
    public int refCnt() {
        return getBytes().refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        getBytes().retain();
        return this;
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        getBytes().retain(increment);
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        getBytes().touch();
        return this;
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        getBytes().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return getBytes().release();
    }

    @Override
    public boolean release(final int decrement) {
        return getBytes().release(decrement);
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link ByteBuf#release()} ownership of {@code getBytes()} is transferred to this {@link
     * PartialReadMessage}.
     */
    @Override
    public ArmedProtocolMessage incrementHopCount() {
        return ArmedProtocolMessage.of(getNonce(), getHopCount().increment(), getNetworkId(), getRecipient(), getSender(), getProofOfWork(), getBytes());
    }

    /**
     * Returns a disarmed version ({@link FullReadMessage}) of this message.
     * <p>
     * Disarming will authenticate the nonce, network id, sender, sender's proof of work, recipient,
     * agreement id, and decrypt all remaining bytes.
     * <p>
     *
     * @param cryptoInstance the crypto instance that should be used
     * @param sessionPair    the {@link SessionPair} to decrypt this message
     * @return the disarmed version of this message
     * @throws InvalidMessageFormatException if disarming was not possible
     */
    @SuppressWarnings({ "java:S1151", "java:S1172", "java:S1452" })
    public FullReadMessage<?> disarm(final Crypto cryptoInstance,
                                     final SessionPair sessionPair) throws InvalidMessageFormatException {
        try {
            getBytes().markReaderIndex();
            try (final ByteBufInputStream in = new ByteBufInputStream(getBytes())) {
                final byte[] decryptedPrivateHeader = cryptoInstance.decrypt(InputStreamHelper.readNBytes(in, PrivateHeader.LENGTH + DrasylSodiumWrapper.XCHACHA20POLY1305_IETF_ABYTES), buildAuthTag(), getNonce(), sessionPair);
                final PrivateHeader privateHeader = PrivateHeader.of(Unpooled.wrappedBuffer(decryptedPrivateHeader));
                final UnsignedShort armedLength = privateHeader.getArmedLength();
                final byte[] decryptedBytes;
                final byte[] undecryptedRemainder;

                if (armedLength.getValue() > 0) {
                    decryptedBytes = cryptoInstance.decrypt(InputStreamHelper.readNBytes(in, armedLength.getValue() + DrasylSodiumWrapper.XCHACHA20POLY1305_IETF_ABYTES), new byte[0], getNonce(), sessionPair);
                    undecryptedRemainder = InputStreamHelper.readAllBytes(in);
                }
                else {
                    decryptedBytes = InputStreamHelper.readAllBytes(in);
                    undecryptedRemainder = new byte[0];
                }

                return UnarmedProtocolMessage.of(
                        getHopCount(),
                        getArmed(),
                        getNetworkId(),
                        getNonce(),
                        getRecipient(),
                        getSender(),
                        getProofOfWork(),
                        Unpooled.wrappedBuffer(decryptedPrivateHeader, decryptedBytes, undecryptedRemainder)).read();
            }
        }
        catch (final IOException | CryptoException e) {
            throw new InvalidMessageFormatException("Unable to disarm message.", e);
        }
        finally {
            getBytes().resetReaderIndex();
        }
    }

    /**
     * Returns a disarmed version ({@link FullReadMessage}) of this message and then releases this
     * message.
     * <p>
     * Disarming will authenticate the nonce, network id, sender, sender's proof of work, recipient,
     * agreement id, and decrypt all remaining bytes.
     * <p>
     *
     * @param cryptoInstance the crypto instance that should be used
     * @param sessionPair    the {@link SessionPair} to decrypt this message
     * @return the disarmed version of this message
     * @throws InvalidMessageFormatException if disarming was not possible
     */
    @SuppressWarnings("java:S1452")
    public FullReadMessage<?> disarmAndRelease(final Crypto cryptoInstance,
                                               final SessionPair sessionPair) throws InvalidMessageFormatException {
        try {
            return disarm(cryptoInstance, sessionPair);
        }
        finally {
            release();
        }
    }

    @Override
    public void writeTo(final ByteBuf out) {
        out.writeInt(MAGIC_NUMBER);
        buildPublicHeader().writeTo(out);
        out.writeBytes(getBytes().slice());
    }

    private PublicHeader buildPublicHeader() {
        return PublicHeader.of(this);
    }

    private byte[] buildAuthTag() {
        return buildPublicHeader().buildAuthTag();
    }

    /**
     * Creates an armed message.
     * <p>
     * {@link ByteBuf#release()} ownership of {@code bytes} is transferred to this {@link
     * PartialReadMessage}.
     * <p>
     * Modifying the content of {@code bytes} or the returned message's buffer affects each other's
     * content while they maintain separate indexes and marks.
     *
     * @param nonce       the nonce
     * @param hopCount    the hop count
     * @param networkId   the network id
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param bytes       the message's remaining armed bytes
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, {@code agreementId}, or {@code
     *                              bytes} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static ArmedProtocolMessage of(final Nonce nonce,
                                          final HopCount hopCount,
                                          final int networkId,
                                          final DrasylAddress recipient,
                                          final DrasylAddress sender,
                                          final ProofOfWork proofOfWork,
                                          final ByteBuf bytes) {
        return new AutoValue_ArmedProtocolMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                recipient,
                hopCount,
                true,
                bytes
        );
    }
}
