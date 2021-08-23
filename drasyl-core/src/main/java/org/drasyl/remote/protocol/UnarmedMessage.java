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
import org.drasyl.annotation.Nullable;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.KeyExchange;
import org.drasyl.remote.protocol.Protocol.KeyExchangeAcknowledgement;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;

import java.io.IOException;

/**
 * Describes an unencrypted message whose only public header has been read so far.
 * <p>
 * {@link #read()} can be used to read the message's remainder.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class UnarmedMessage implements PartialReadMessage {
    @Nullable
    public abstract IdentityPublicKey getRecipient();

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
    public UnarmedMessage incrementHopCount() {
        return UnarmedMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getAgreementId(), getBytes().retain());
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

    /**
     * Read the remainder of this message and returns the resulted {@link FullReadMessage}.
     * <p>
     * {@code getBytes()} will be consumed and released by this method.
     *
     * @return the fully read message
     * @throws InvalidMessageFormatException if message could not be read
     */
    @SuppressWarnings({ "java:S138", "java:S1142", "java:S1151", "java:S1452" })
    public FullReadMessage<?> read() throws InvalidMessageFormatException {
        try (final ByteBufInputStream in = new ByteBufInputStream(getBytes())) {
            final PrivateHeader privateHeader = PrivateHeader.parseDelimitedFrom(in);

            if (privateHeader == null) {
                throw new InvalidMessageFormatException("No private header found. Stream is at EOF.");
            }

            switch (privateHeader.getType()) {
                case ACKNOWLEDGEMENT:
                    return AcknowledgementMessage.of(
                            getNonce(),
                            getNetworkId(),
                            getSender(),
                            getProofOfWork(),
                            getRecipient(),
                            getHopCount(),
                            getAgreementId(),
                            Acknowledgement.parseDelimitedFrom(in)
                    );
                case APPLICATION:
                    return ApplicationMessage.of(
                            getNonce(),
                            getNetworkId(),
                            getSender(),
                            getProofOfWork(),
                            getRecipient(),
                            getHopCount(),
                            getAgreementId(),
                            getBytes().slice().retain()
                    );
                case UNITE:
                    return UniteMessage.of(
                            getNonce(),
                            getNetworkId(),
                            getSender(),
                            getProofOfWork(),
                            getRecipient(),
                            getHopCount(),
                            getAgreementId(),
                            Unite.parseDelimitedFrom(in)
                    );
                case DISCOVERY:
                    return DiscoveryMessage.of(
                            getNonce(),
                            getNetworkId(),
                            getSender(),
                            getProofOfWork(),
                            getRecipient(),
                            getHopCount(),
                            getAgreementId(),
                            Discovery.parseDelimitedFrom(in)
                    );
                case KEY_EXCHANGE:
                    return KeyExchangeMessage.of(
                            getNonce(),
                            getNetworkId(),
                            getSender(),
                            getProofOfWork(),
                            getRecipient(),
                            getHopCount(),
                            getAgreementId(),
                            KeyExchange.parseDelimitedFrom(in)
                    );
                case KEY_EXCHANGE_ACKNOWLEDGEMENT:
                    return KeyExchangeAcknowledgementMessage.of(
                            getNonce(),
                            getNetworkId(),
                            getSender(),
                            getProofOfWork(),
                            getRecipient(),
                            getHopCount(),
                            getAgreementId(),
                            KeyExchangeAcknowledgement.parseDelimitedFrom(in)
                    );
                default:
                    throw new InvalidMessageFormatException("Message is not of any known type.");
            }
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Unable to read full message.", e);
        }
        finally {
            getBytes().release();
        }
    }

    private PublicHeader buildPublicHeader() {
        final PublicHeader.Builder builder = PublicHeader.newBuilder()
                .setNonce(getNonce().toByteString())
                .setNetworkId(getNetworkId())
                .setSender(getSender().getBytes())
                .setProofOfWork(getProofOfWork().intValue())
                .setHopCount(getHopCount().getByte());

        if (getRecipient() != null) {
            builder.setRecipient(getRecipient().getBytes());
        }

        if (getAgreementId() != null) {
            builder.setAgreementId(getAgreementId().toByteString());
        }

        return builder.build();
    }

    /**
     * Returns an armed version ({@link ArmedMessage}) of this message.
     *
     * @param cryptoInstance the crypto instance that should be used
     * @param sessionPair    will be used for encryption
     * @return the armed version ({@link ArmedMessage}) of this message
     * @throws InvalidMessageFormatException if arming was not possible
     */
    public ArmedMessage arm(final Crypto cryptoInstance,
                            final SessionPair sessionPair) throws InvalidMessageFormatException {
        try {
            getBytes().markReaderIndex();
            try (final ByteBufInputStream in = new ByteBufInputStream(getBytes())) {
                final byte[] encryptedBytes = cryptoInstance.encrypt(in.readAllBytes(), buildAuthTag(), getNonce(), sessionPair);

                return ArmedMessage.of(
                        getNonce(),
                        getNetworkId(),
                        getSender(),
                        getProofOfWork(),
                        getRecipient(),
                        getHopCount(),
                        getAgreementId(),
                        Unpooled.wrappedBuffer(encryptedBytes)
                );
            }
        }
        catch (final IOException | CryptoException e) {
            throw new InvalidMessageFormatException("Unable to arm message.", e);
        }
        finally {
            getBytes().resetReaderIndex();
        }
    }

    /**
     * Returns an armed version ({@link ArmedMessage}) of this message and then releases this
     * message.
     *
     * @param cryptoInstance the crypto instance that should be used
     * @param sessionPair    will be used for encryption
     * @return the armed version ({@link ArmedMessage}) of this message
     * @throws InvalidMessageFormatException if arming was not possible
     */
    public ArmedMessage armAndRelease(final Crypto cryptoInstance,
                                      final SessionPair sessionPair) throws InvalidMessageFormatException {
        try {
            return arm(cryptoInstance, sessionPair);
        }
        finally {
            release();
        }
    }

    private byte[] buildAuthTag() {
        return buildPublicHeader().toBuilder().clearHopCount().build().toByteArray();
    }

    /**
     * Creates an unarmed message.
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
     * @param bytes       message's remainder as bytes (may be armed). {@link ByteBuf#release()}
     *                    ownership is transferred to this {@link PartialReadMessage}.
     * @return an {@link PartialReadMessage}
     * @throws NullPointerException if {@code nonce}, {@code sender}, {@code proofOfWork}, or {@code
     *                              recipient} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static UnarmedMessage of(final Nonce nonce,
                                    final int networkId,
                                    final IdentityPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final IdentityPublicKey recipient,
                                    final HopCount hopCount,
                                    final AgreementId agreementId,
                                    final ByteBuf bytes) {
        return new AutoValue_UnarmedMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                agreementId,
                bytes,
                recipient
        );
    }
}
