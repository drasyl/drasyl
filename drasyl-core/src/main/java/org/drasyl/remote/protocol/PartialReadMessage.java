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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.util.ReferenceCounted;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.UnsignedShort;

import java.io.IOException;
import java.util.Arrays;

/**
 * Describes a message whose content has been read partially. This is the case for encrypted
 * messages, chunks or messages that do not need to be read completely.
 *
 * @see FullReadMessage
 */
public interface PartialReadMessage extends RemoteMessage, ReferenceCounted, AutoCloseable {
    /**
     * Returns the remainder of unread bytes of this message.
     *
     * @return the remainder of unread bytes of this message
     */
    ByteBuf getBytes();

    /**
     * Calls {@link ByteBuf#release()} on {@link #getBytes()}.
     */
    @Override
    void close();

    /**
     * Creates a {@link PartialReadMessage} from {@code publicHeader} and {@code bytes}.
     * <ul>
     * <li>If {@code publicHeader.getTotalChunks()} is positive, a {@link HeadChunkMessage} object is returned.</li>
     * <li>If {@code publicHeader.getChunkNo()} is positive, a {@link BodyChunkMessage} object is returned.</li>
     * <li>In case {@code publicHeader.getAgreementId()} is not empty, {@link ArmedMessage} object is returned.</li>
     * <li>In all other cases, {@link UnarmedMessage} object is returned.</li>
     * </ul>
     * <p>
     * {@link ByteBuf#release()} ownership of {@code bytes} is transferred to this {@link
     * PartialReadMessage}.
     * <p>
     * Modifying the content of {@code bytes} or the returned message's buffer affects each other's
     * content while they maintain separate indexes and marks.
     *
     * @param publicHeader message's public header
     * @param bytes        message's remainder as bytes (may be armed). {@link ByteBuf#release()}
     *                     ownership is transferred to this {@link PartialReadMessage}.
     * @return an {@link PartialReadMessage} object
     * @throws NullPointerException     if {@code publicHeader.getNonce()}, {@code
     *                                  publicHeader.getSender()}, {@code publicHeader.getProofOfWork()},
     *                                  or {@code publicHeader.getRecipient()} is {@code null}
     * @throws IllegalArgumentException if {@code publicHeader.getSender()} or {@code
     *                                  publicHeader.getRecipient()} has wrong key size, or {@code
     *                                  publicHeader.getAgreementId()} is neither {@code null} nor a
     *                                  valid SHA256 hash
     */
    @SuppressWarnings("java:S1142")
    static PartialReadMessage of(final PublicHeader publicHeader,
                                 final ByteBuf bytes) {
        try {
            if (publicHeader.getTotalChunks() > 0) {
                return HeadChunkMessage.of(
                        Nonce.of(publicHeader.getNonce()),
                        publicHeader.getNetworkId(),
                        IdentityPublicKey.of(publicHeader.getSender()),
                        ProofOfWork.of(publicHeader.getProofOfWork()),
                        IdentityPublicKey.of(publicHeader.getRecipient()),
                        HopCount.of((byte) publicHeader.getHopCount()),
                        UnsignedShort.of(publicHeader.getTotalChunks()),
                        bytes
                );
            }
            else if (publicHeader.getChunkNo() > 0) {
                return BodyChunkMessage.of(
                        Nonce.of(publicHeader.getNonce()),
                        publicHeader.getNetworkId(),
                        IdentityPublicKey.of(publicHeader.getSender()),
                        ProofOfWork.of(publicHeader.getProofOfWork()),
                        IdentityPublicKey.of(publicHeader.getRecipient()),
                        HopCount.of((byte) publicHeader.getHopCount()),
                        UnsignedShort.of(publicHeader.getChunkNo()),
                        bytes
                );
            }
            else if (!publicHeader.getAgreementId().isEmpty()) {
                return ArmedMessage.of(
                        Nonce.of(publicHeader.getNonce()),
                        publicHeader.getNetworkId(),
                        IdentityPublicKey.of(publicHeader.getSender()),
                        ProofOfWork.of(publicHeader.getProofOfWork()),
                        IdentityPublicKey.of(publicHeader.getRecipient()),
                        HopCount.of((byte) publicHeader.getHopCount()),
                        AgreementId.of(publicHeader.getAgreementId()),
                        bytes
                );
            }
            else {
                final AgreementId agreementId;
                if (publicHeader.getAgreementId().isEmpty()) {
                    agreementId = null;
                }
                else {
                    agreementId = AgreementId.of(publicHeader.getAgreementId());
                }

                final IdentityPublicKey recipient;
                if (publicHeader.getRecipient().isEmpty()) {
                    recipient = null;
                }
                else {
                    recipient = IdentityPublicKey.of(publicHeader.getRecipient());
                }
                return UnarmedMessage.of(
                        Nonce.of(publicHeader.getNonce()),
                        publicHeader.getNetworkId(),
                        IdentityPublicKey.of(publicHeader.getSender()),
                        ProofOfWork.of(publicHeader.getProofOfWork()),
                        recipient,
                        HopCount.of((byte) publicHeader.getHopCount()),
                        agreementId,
                        bytes
                );
            }
        }
        finally {
            bytes.discardSomeReadBytes();
        }
    }

    /**
     * Creates a {@link PartialReadMessage} from {@code bytes}. First, this method checks if {@code
     * bytes} starts with {@link #MAGIC_NUMBER}, then reads the message's public header:
     * <ul>
     * <li>If {@code publicHeader.getTotalChunks()} is positive, a {@link HeadChunkMessage} object is returned.</li>
     * <li>If {@code publicHeader.getChunkNo()} is positive, a {@link BodyChunkMessage} object is returned.</li>
     * <li>In case {@code publicHeader.getAgreementId()} is not empty, {@link ArmedMessage} object is returned.</li>
     * <li>In all other cases, {@link UnarmedMessage} object is returned.</li>
     * </ul>
     * <p>
     * Modifying the content of {@code bytes} or the returned message's buffer affects each other's
     * content while they maintain separate indexes and marks.
     *
     * @param bytes message's bytes (may be partially armed). {@link ByteBuf#release()} ownership is
     *              transferred to this {@link PartialReadMessage}.
     * @return an {@link PartialReadMessage} object
     * @throws NullPointerException          if {@code publicHeader.getNonce()}, {@code
     *                                       publicHeader.getSender()}, {@code publicHeader.getProofOfWork()},
     *                                       or {@code publicHeader.getRecipient()} is {@code null}
     * @throws IllegalArgumentException      if {@code publicHeader.getSender()} or {@code
     *                                       publicHeader.getRecipient()} has wrong key size, or
     *                                       {@code publicHeader.getAgreementId()} is neither {@code
     *                                       null} nor a valid SHA256 hash
     * @throws InvalidMessageFormatException if magic number or public header is missing
     */
    static PartialReadMessage of(final ByteBuf bytes) throws InvalidMessageFormatException {
        try (final ByteBufInputStream in = new ByteBufInputStream(bytes)) {
            final byte[] magicNumber = in.readNBytes(MAGIC_NUMBER_LENGTH);

            if (!Arrays.equals(MAGIC_NUMBER.toByteArray(), magicNumber)) {
                throw new InvalidMessageFormatException("Magic Number mismatch!");
            }

            return of(PublicHeader.parseDelimitedFrom(in), bytes.slice());
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Can't read public header.", e);
        }
    }
}
