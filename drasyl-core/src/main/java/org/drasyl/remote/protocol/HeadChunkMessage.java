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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.util.ReferenceCounted;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.UnsignedShort;

import java.io.IOException;

/**
 * Describes a head chunk of a {@link RemoteMessage}, which specifies how many chunks a message was
 * divided to.
 * <p>
 * This is an immutable object.
 *
 * @see BodyChunkMessage
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class HeadChunkMessage implements ChunkMessage {
    /**
     * Returns the total number of chunks.
     *
     * @return the total number of chunks.
     */
    public abstract UnsignedShort getTotalChunks();

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
    public HeadChunkMessage incrementHopCount() {
        return HeadChunkMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getTotalChunks(), getBytes().retain());
    }

    @Override
    public void writeTo(final ByteBuf out) throws InvalidMessageFormatException {
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(out)) {
            outputStream.writeInt(MAGIC_NUMBER);
            buildPublicHeader().writeDelimitedTo(outputStream);
            out.writeBytes(getBytes().slice());
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Can't write nonce or public header to given ByteBuf.", e);
        }
    }

    private PublicHeader buildPublicHeader() {
        return PublicHeader.newBuilder()
                .setNonce(getNonce().toByteString())
                .setNetworkId(getNetworkId())
                .setSender(getSender().getBytes())
                .setProofOfWork(getProofOfWork().intValue())
                .setRecipient(getRecipient().getBytes())
                .setHopCount(getHopCount().getByte())
                .setTotalChunks(getTotalChunks().getValue())
                .build();
    }

    /**
     * Creates a head chunk message.
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
     * @param totalChunks the number of total chunks
     * @param bytes       the message's remaining armed bytes
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code chunkNo}, or
     *                                  {@code bytes} is {@code null}
     * @throws IllegalArgumentException if {@code totalChunks} is not positive
     */
    @SuppressWarnings("java:S107")
    public static HeadChunkMessage of(final Nonce nonce,
                                      final int networkId,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final IdentityPublicKey recipient,
                                      final HopCount hopCount,
                                      final UnsignedShort totalChunks,
                                      final ByteBuf bytes) {
        if (totalChunks.getValue() < 1) {
            throw new IllegalArgumentException("totalChunks must be positive");
        }

        return new AutoValue_HeadChunkMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                recipient,
                hopCount,
                null,
                bytes,
                totalChunks
        );
    }
}
