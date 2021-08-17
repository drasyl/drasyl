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
package org.drasyl.remote.protocol;

import com.google.auto.value.AutoValue;
import com.goterl.lazysodium.utils.SessionPair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.drasyl.annotation.NonNull;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.PublicHeader;

import java.io.IOException;

/**
 * Describes a message whose contents has been armed by using authenticated encryption with
 * associated data.
 * <p>
 * Only the message recipient can decrypt and authenticate the message by calling {@link
 * #disarm(Crypto, SessionPair)}.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class ArmedMessage implements PartialReadMessage {
    @NonNull
    public abstract AgreementId getAgreementId();

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
        return getBytes().retain();
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        return getBytes().retain(increment);
    }

    @Override
    public ReferenceCounted touch() {
        return getBytes().touch();
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        return getBytes().touch(hint);
    }

    @Override
    public boolean release() {
        return getBytes().release();
    }

    @Override
    public boolean release(final int decrement) {
        return getBytes().release(decrement);
    }

    @Override
    public ArmedMessage incrementHopCount() {
        return ArmedMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getAgreementId(), getBytes().retain());
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
                final byte[] decryptedBytes = cryptoInstance.decrypt(in.readAllBytes(), buildAuthTag(), getNonce(), sessionPair);

                return UnarmedMessage.of(
                        getNonce(),
                        getNetworkId(),
                        getSender(),
                        getProofOfWork(),
                        getRecipient(),
                        getHopCount(),
                        getAgreementId(),
                        Unpooled.wrappedBuffer(decryptedBytes)
                ).read();
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
    public void writeTo(final ByteBuf out) throws InvalidMessageFormatException {
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(out)) {
            MAGIC_NUMBER.writeTo(outputStream);
            buildPublicHeader().writeDelimitedTo(outputStream);
            out.writeBytes(getBytes().slice());
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Can't write nonce or public header to given ByteBuf.", e);
        }
    }

    private PublicHeader buildPublicHeader() {
        final PublicHeader.Builder builder = PublicHeader.newBuilder()
                .setNonce(getNonce().toByteString())
                .setNetworkId(getNetworkId())
                .setSender(getSender().getBytes())
                .setProofOfWork(getProofOfWork().intValue())
                .setRecipient(getRecipient().getBytes())
                .setHopCount(getHopCount().getByte())
                .setAgreementId(getAgreementId().toByteString());

        return builder.build();
    }

    private byte[] buildAuthTag() {
        return buildPublicHeader().toBuilder().clearHopCount().build().toByteArray();
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
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @param bytes       the message's remaining armed bytes
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, {@code agreementId}, or {@code
     *                              bytes} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static ArmedMessage of(final Nonce nonce,
                                  final int networkId,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final IdentityPublicKey recipient,
                                  final HopCount hopCount,
                                  final AgreementId agreementId,
                                  final ByteBuf bytes) {
        return new AutoValue_ArmedMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                recipient,
                hopCount,
                bytes,
                agreementId
        );
    }
}
