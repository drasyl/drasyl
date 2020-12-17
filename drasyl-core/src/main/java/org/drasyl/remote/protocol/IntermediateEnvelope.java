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
package org.drasyl.remote.protocol;

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.message.MessageId;
import org.drasyl.remote.message.RemoteMessage;
import org.drasyl.remote.message.UserAgent;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;

import java.io.IOException;

/**
 * This class allows to read a given {@link ByteBuf} encoded protobuf message in parts with only
 * decoding the requested parts of the given {@link ByteBuf}. If a part was request it will be
 * translated into a Java object.
 */
public class IntermediateEnvelope implements ReferenceCounted, RemoteMessage {
    private final ByteBuf originalMessage;
    private final ByteBuf message;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private MessageLite body;

    private IntermediateEnvelope(final ByteBuf message) {
        if (!message.isReadable()) {
            throw new IllegalArgumentException("The given message has no readable data.");
        }

        this.originalMessage = message;
        this.message = message.duplicate();
        this.publicHeader = null;
        this.privateHeader = null;
        this.body = null;
    }

    /**
     * <b>Note: The given {@code message} {@link ByteBuf} will not be modified. This object
     * uses a duplicate of the given {@link ByteBuf}.</b>
     *
     * @param message the message that should be wrapped
     * @return an IntermediateEnvelope
     * @throws IllegalArgumentException if the given {@link ByteBuf} is not readable
     */
    public static IntermediateEnvelope of(final ByteBuf message) {
        return new IntermediateEnvelope(message);
    }

    /**
     * Creates a message envelope from {@code publicHeader}, {@code privateHeader}, and {@code
     * body}.
     * <p>
     * Node: This method will use a pooled {@link ByteBuf}.
     *
     * @param publicHeader  message's public header
     * @param privateHeader message's private header
     * @param body          the message
     * @return an IntermediateEnvelope
     * @throws IOException if {@code publicHeader}, {@code privateHeader}, and {@code body} can not
     *                     be serialized
     */
    public static IntermediateEnvelope of(final PublicHeader publicHeader,
                                          final PrivateHeader privateHeader,
                                          final MessageLite body) throws IOException {
        final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            publicHeader.writeDelimitedTo(outputStream);
            privateHeader.writeDelimitedTo(outputStream);
            body.writeDelimitedTo(outputStream);

            return of(byteBuf);
        }
        catch (final IOException e) {
            byteBuf.release();
            throw e;
        }
    }

    /**
     * Reads only the public header of the given message and retains the underlying byte
     * representation of the full message.
     *
     * @return header of the message
     * @throws IOException if the public header cannot be read read
     */
    public PublicHeader getPublicHeader() throws IOException {
        synchronized (this) {
            if (publicHeader == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    publicHeader = PublicHeader.parseDelimitedFrom(in);
                }
                catch (final IOException e) {
                    throw new IOException("Can't read public header of the given message do to the following exception: ", e);
                }
            }

            return publicHeader;
        }
    }

    /**
     * Reads only the (private) header of the given message and retains the underlying byte
     * representation of the full message.
     *
     * @return the private header
     * @throws IOException if the private header cannot be read read
     */
    public PrivateHeader getPrivateHeader() throws IOException {
        synchronized (this) {
            getPublicHeader();
            if (privateHeader == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    privateHeader = PrivateHeader.parseDelimitedFrom(in);
                }
                catch (final IOException e) {
                    throw new IOException("Can't read private header of the given message do to the following exception: ", e);
                }
            }

            return privateHeader;
        }
    }

    /**
     * Reads only the body of the given message  and retains the underlying byte representation of
     * the full message.
     *
     * @return the body
     * @throws IOException if the body cannot be read read
     */
    public MessageLite getBody() throws IOException {
        synchronized (this) {
            getPrivateHeader();
            if (body == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    switch (privateHeader.getType()) {
                        case ACKNOWLEDGEMENT:
                            body = Acknowledgement.parseDelimitedFrom(in);
                            break;
                        case APPLICATION:
                            body = Application.parseDelimitedFrom(in);
                            break;
                        case UNITE:
                            body = Unite.parseDelimitedFrom(in);
                            break;
                        case DISCOVERY:
                            body = Discovery.parseDelimitedFrom(in);
                            break;
                        default:
                            throw new IOException("Message is not of any known type.");
                    }
                }
                catch (final IOException e) {
                    throw new IOException("Can't read the given message do to the following exception: ", e);
                }
            }

            return body;
        }
    }

    public ByteBuf getByteBuf() {
        synchronized (this) {
            return originalMessage;
        }
    }

    ByteBuf getInternalByteBuf() {
        synchronized (this) {
            return message;
        }
    }

    @Override
    public int refCnt() {
        return originalMessage.refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        return originalMessage.retain();
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        return originalMessage.retain(increment);
    }

    @Override
    public ReferenceCounted touch() {
        return originalMessage.touch();
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        return originalMessage.touch(hint);
    }

    @Override
    public boolean release() {
        return originalMessage.release();
    }

    @Override
    public boolean release(final int decrement) {
        return originalMessage.release(decrement);
    }

    @Override
    public MessageId getId() {
        try {
            return MessageId.of(getPublicHeader().getId().toByteArray());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public UserAgent getUserAgent() {
        try {
            return new UserAgent(getPublicHeader().toByteArray());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public int getNetworkId() {
        try {
            return getPublicHeader().getNetworkId();
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public CompressedPublicKey getSender() {
        try {
            return CompressedPublicKey.of(getPublicHeader().getSender().toByteArray());
        }
        catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public ProofOfWork getProofOfWork() {
        try {
            return ProofOfWork.of(getPublicHeader().getProofOfWork());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public CompressedPublicKey getRecipient() {
        try {
            return CompressedPublicKey.of(getPublicHeader().getRecipient().toByteArray());
        }
        catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public byte getHopCount() {
        try {
            return getPublicHeader().getHopCount().byteAt(0);
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void incrementHopCount() {
        // do nothing
    }

    @Override
    public Signature getSignature() {
        try {
            return new Signature(getPublicHeader().getSignature().toByteArray());
        }
        catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void setSignature(final Signature signature) {
        // do nothing
    }
}
