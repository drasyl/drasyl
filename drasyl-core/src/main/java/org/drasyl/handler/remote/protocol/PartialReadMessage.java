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

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;

import java.io.IOException;

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
     * Calls {@link #release()}.
     */
    @Override
    void close();

    /**
     * Creates a {@link PartialReadMessage} from {@code publicHeader} and {@code bytes}.
     * <ul>
     * <li>In case {@code publicHeader.getArmed()} is {@code true}, {@link ArmedProtocolMessage} object is returned.</li>
     * <li>In all other cases, {@link UnarmedProtocolMessage} object is returned.</li>
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
        if (publicHeader.getArmed()) {
            return ArmedProtocolMessage.of(
                    publicHeader.getNonce(),
                    publicHeader.getHopCount(),
                    publicHeader.getNetworkId(),
                    publicHeader.getRecipient(),
                    publicHeader.getSender(),
                    publicHeader.getProofOfWork(),
                    bytes
            );
        }
        else {

            return UnarmedProtocolMessage.of(
                    publicHeader.getHopCount(),
                    publicHeader.getArmed(),
                    publicHeader.getNetworkId(),
                    publicHeader.getNonce(),
                    publicHeader.getRecipient(),
                    publicHeader.getSender(),
                    publicHeader.getProofOfWork(),
                    bytes);
        }
    }

    /**
     * Creates a {@link PartialReadMessage} from {@code bytes}. First, this method checks if {@code
     * bytes} starts with {@link #MAGIC_NUMBER}, then reads the message's public header:
     * <ul>
     * <li>In case {@code publicHeader.getArmed()} is {@code true}, {@link ArmedProtocolMessage} object is returned.</li>
     * <li>In all other cases, {@link UnarmedProtocolMessage} object is returned.</li>
     * </ul>
     * <p>
     * {@link ByteBuf#release()} ownership of {@code bytes} is transferred to this {@link
     * PartialReadMessage}.
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
        if (bytes.readableBytes() < Integer.BYTES || MAGIC_NUMBER != bytes.readInt()) {
            throw new MagicNumberMissmatchException();
        }

        try {
            return of(PublicHeader.of(bytes), bytes);
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Can't read public header.", e);
        }
    }
}
