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

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.InternetDiscovery;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.MessageType;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.ByteBufUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;

/**
 * This class allows to read a given {@link ByteBuf} encoded protobuf message in parts with only
 * decoding the requested parts of the given {@link ByteBuf}. If a part was request it will be
 * translated into a Java object.
 */
public class RemoteEnvelope<T extends MessageLite> implements ReferenceCounted, AutoCloseable {
    private static final byte[] MAGIC_NUMBER = new byte[]{ 0x1E, 0x3F, 0x50, 0x01 };
    public static final short MAGIC_NUMBER_LENGTH = 4;
    private ByteBuf message;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private T body;

    RemoteEnvelope(final ByteBuf message,
                   final PublicHeader publicHeader,
                   final PrivateHeader privateHeader,
                   final T body) {
        this.message = message;
        this.publicHeader = publicHeader;
        this.privateHeader = privateHeader;
        this.body = body;
    }

    private RemoteEnvelope(final ByteBuf message) throws InvalidMessageFormatException {
        if (!message.isReadable()) {
            try {
                throw new InvalidMessageFormatException("The given message has no readable data.");
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        this.message = message.duplicate();
        this.publicHeader = null;
        this.privateHeader = null;
        this.body = null;
    }

    private RemoteEnvelope(final PublicHeader publicHeader,
                           final PrivateHeader privateHeader,
                           final T body) {
        this.message = null;
        this.publicHeader = publicHeader;
        this.privateHeader = privateHeader;
        this.body = body;
    }

    @Override
    public String toString() {
        return "RemoteEnvelope{" +
                "message=" + message +
                ", publicHeader=" + (publicHeader != null ? TextFormat.shortDebugString(publicHeader) : null) +
                ", privateHeader=" + (privateHeader != null ? TextFormat.shortDebugString(privateHeader) : null) +
                ", body=" + (body instanceof MessageOrBuilder ? TextFormat.shortDebugString((MessageOrBuilder) body) : null) +
                '}';
    }

    @Override
    public void close() {
        releaseAll();
    }

    /**
     * Wraps the given {@link ByteBuf} into an {@link RemoteEnvelope}.
     * <p>
     * <b>Note: The given {@code message} {@link ByteBuf} will not be modified. This object
     * uses a duplicate of the given {@link ByteBuf}.</b>
     * <p>
     * {@link ByteBuf#release()} ownership of {@code message} is transferred to this {@link
     * RemoteEnvelope}.
     *
     * @param message the message that should be wrapped. {@link ByteBuf#release()} ownership is
     *                transferred to this {@link RemoteEnvelope}.
     * @return an RemoteEnvelope
     * @throws InvalidMessageFormatException if the given {@link ByteBuf} is not readable
     */
    public static <T extends MessageLite> RemoteEnvelope<T> of(final ByteBuf message) throws InvalidMessageFormatException {
        return new RemoteEnvelope<>(message);
    }

    /**
     * Creates a message envelope from {@code publicHeader}, {@code privateHeader}, and {@code
     * body}.
     *
     * @param publicHeader  message's public header
     * @param privateHeader message's private header
     * @param body          the message
     * @return an RemoteEnvelope
     */
    public static <T extends MessageLite> RemoteEnvelope<T> of(final PublicHeader publicHeader,
                                                               final PrivateHeader privateHeader,
                                                               final T body) {
        return new RemoteEnvelope<>(publicHeader, privateHeader, body);
    }

    /**
     * Creates a message envelope from {@code publicHeader} and {@code bytes}
     *
     * @param publicHeader message's public header
     * @param bytes        message's remainder as bytes (may be encrypted)
     * @return an RemoteEnvelope
     * @throws InvalidMessageFormatException if {@code publicHeader} and {@code bytes} can not be
     *                                       serialized
     */
    public static <T extends MessageLite> RemoteEnvelope<T> of(final PublicHeader publicHeader,
                                                               final byte[] bytes) throws InvalidMessageFormatException {
        return of(publicHeader, Unpooled.wrappedBuffer(bytes));
    }

    /**
     * Creates a message envelope from {@code publicHeader} and {@code bytes}.
     * <p>
     * {@link ByteBuf#release()} ownership of {@code bytes} is transferred to this {@link
     * RemoteEnvelope}.
     *
     * @param publicHeader message's public header
     * @param bytes        message's remainder as bytes (may be encrypted). {@link
     *                     ByteBuf#release()} ownership is transferred to this {@link
     *                     RemoteEnvelope}.
     * @return an RemoteEnvelope
     * @throws InvalidMessageFormatException if {@code publicHeader} and {@code bytes} can not be
     *                                       serialized
     */
    public static <T extends MessageLite> RemoteEnvelope<T> of(final PublicHeader publicHeader,
                                                               final ByteBuf bytes) throws InvalidMessageFormatException {
        final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            outputStream.write(MAGIC_NUMBER);
            publicHeader.writeDelimitedTo(outputStream);
            byteBuf.writeBytes(bytes);

            return of(byteBuf);
        }
        catch (final IOException e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            throw new InvalidMessageFormatException(e);
        }
        finally {
            bytes.release();
        }
    }

    /**
     * Reads only the public header of the given message and retains the underlying byte
     * representation of the full message.
     *
     * @return header of the message
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public PublicHeader getPublicHeader() throws InvalidMessageFormatException {
        synchronized (this) {
            if (publicHeader == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    final byte[] magicNumber = in.readNBytes(MAGIC_NUMBER_LENGTH);

                    if (!Arrays.equals(MAGIC_NUMBER, magicNumber)) {
                        throw new InvalidMessageFormatException("Magic Number mismatch!");
                    }

                    publicHeader = PublicHeader.parseDelimitedFrom(in);
                }
                catch (final IOException e) {
                    throw new InvalidMessageFormatException("Can't read public header of the given message do to the following exception: ", e);
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
     * @throws InvalidMessageFormatException if the private header cannot be read
     */
    public PrivateHeader getPrivateHeader() throws InvalidMessageFormatException {
        synchronized (this) {
            getPublicHeader();
            if (privateHeader == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    privateHeader = PrivateHeader.parseDelimitedFrom(in);
                }
                catch (final IOException e) {
                    throw new InvalidMessageFormatException("Can't read private header of the given message do to the following exception: ", e);
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
     * @throws InvalidMessageFormatException if the body cannot be read
     */
    public T getBody() throws InvalidMessageFormatException {
        synchronized (this) {
            getPrivateHeader();
            if (body == null) {
                try (final ByteBufInputStream in = new ByteBufInputStream(message)) {
                    body = bodyFromInputStream(privateHeader.getType(), in);
                }
                catch (final IOException e) {
                    throw new InvalidMessageFormatException("Can't read the given message do to the following exception: ", e);
                }
            }

            return body;
        }
    }

    /**
     * Reads only the body of the given message and releases the underlying byte representation of
     * the full message.
     *
     * @return the body
     * @throws InvalidMessageFormatException if the body cannot be read
     */
    public T getBodyAndRelease() throws InvalidMessageFormatException {
        synchronized (this) {
            try {
                return getBody();
            }
            finally {
                releaseAll();
            }
        }
    }

    /**
     * This method returns a copy of the {@link RemoteEnvelope} and resets the reader index to 0.
     *
     * @return the wrapped {@link ByteBuf} of this envelope
     */
    public ByteBuf copy() {
        synchronized (this) {
            if (message != null) {
                return message.duplicate().readerIndex(0);
            }

            return null;
        }
    }

    /**
     * This method returns the internal wrapped {@link ByteBuf} of this {@link RemoteEnvelope}.
     *
     * @return the internal wrapped {@link ByteBuf}
     */
    public ByteBuf getInternalByteBuf() {
        synchronized (this) {
            return message;
        }
    }

    /**
     * The <b>external</b> {@link ByteBuf} or if {@code null} builds it first.
     *
     * @return the envelope as {@link ByteBuf}
     * @throws InvalidMessageFormatException if the envelope can't be build
     */
    public ByteBuf getOrBuildByteBuf() throws InvalidMessageFormatException {
        synchronized (this) {
            if (message == null || message.writerIndex() == 0) {
                this.message = proto2ByteBuf();
            }

            return copy();
        }
    }

    /**
     * The <b>internal</b> {@link ByteBuf} or if {@code null} builds it first.
     *
     * @return the envelope as {@link ByteBuf}
     * @throws InvalidMessageFormatException if the envelope can't be build
     */
    ByteBuf getOrBuildInternalByteBuf() throws InvalidMessageFormatException {
        synchronized (this) {
            getOrBuildByteBuf();

            return message;
        }
    }

    @Override
    public int refCnt() {
        if (message != null) {
            return message.refCnt();
        }

        return 0;
    }

    @Override
    public ReferenceCounted retain() {
        if (message != null) {
            message.retain();
        }
        return this;
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        if (message != null) {
            message.retain(increment);
        }
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        if (message != null) {
            message.touch();
        }
        return this;
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        if (message != null) {
            message.touch(hint);
        }
        return this;
    }

    @Override
    public boolean release() {
        return ReferenceCountUtil.release(message);
    }

    @Override
    public boolean release(final int decrement) {
        if (message != null) {
            return message.release(decrement);
        }

        return true;
    }

    /**
     * This method does release all used {@link ByteBuf}s. The corresponding elements where also set
     * to {@code null}.
     */
    public void releaseAll() {
        ReferenceCountUtil.safeRelease(message);

        message = null;
    }

    /**
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public MessageId getId() throws InvalidMessageFormatException {
        return MessageId.of(getPublicHeader().getId());
    }

    /**
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public int getNetworkId() throws InvalidMessageFormatException {
        return getPublicHeader().getNetworkId();
    }

    /**
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public CompressedPublicKey getSender() throws InvalidMessageFormatException {
        return CompressedPublicKey.of(getPublicHeader().getSender().toByteArray());
    }

    /**
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public ProofOfWork getProofOfWork() throws InvalidMessageFormatException {
        return ProofOfWork.of(getPublicHeader().getProofOfWork());
    }

    /**
     * Returns the {@link CompressedPublicKey} of the message recipient. If the message has no
     * recipient (e.g. because it is a multicast message) {@code null} is returned.
     *
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public CompressedPublicKey getRecipient() throws InvalidMessageFormatException {
        final byte[] bytes = getPublicHeader().getRecipient().toByteArray();
        if (bytes.length == 0) {
            return null;
        }
        else {
            return CompressedPublicKey.of(bytes);
        }
    }

    /**
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public byte getHopCount() throws InvalidMessageFormatException {
        return (byte) (getPublicHeader().getHopCount() - 1);
    }

    /**
     * @throws InvalidMessageFormatException if the public header cannot be read or be updated
     */
    public void incrementHopCount() throws InvalidMessageFormatException {
        synchronized (this) {
            final PublicHeader existingPublicHeader = getPublicHeader();
            final byte newHopCount = (byte) (existingPublicHeader.getHopCount() + 1);

            if (newHopCount == 0) {
                throw new InvalidMessageFormatException("hop count overflow");
            }

            this.publicHeader = PublicHeader.newBuilder(existingPublicHeader)
                    .setHopCount(newHopCount)
                    .build();

            if (message != null) {
                final ByteBuf publicHeaderByteBuf = PooledByteBufAllocator.DEFAULT.buffer();

                try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(publicHeaderByteBuf)) {
                    publicHeader.writeDelimitedTo(outputStream);

                    this.message = ByteBufUtil.prepend(message, Unpooled.copiedBuffer(MAGIC_NUMBER), publicHeaderByteBuf);
                }
                catch (final IOException e) {
                    throw new InvalidMessageFormatException(e);
                }
            }
        }
    }

    /**
     * @return signature as byte array
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public byte[] getSignature() throws InvalidMessageFormatException {
        return getPublicHeader().getSignature().toByteArray();
    }

    /**
     * Returns an armed version of this envelope for sending it through untrustworthy channels.
     * <p>
     * {@code privateKey} must match message's sender.
     * <p>
     * Arming includes the following steps:
     * <ul>
     * <li>encrypted {@link #privateHeader} and encrypted {@link #body} will be signed with {@code privateKey}</li>
     * <li>{@link #privateHeader} and {@link #body} will be encrypted with recipient's public key (not implemented!)</li>
     * </ul>
     * <p>
     * <b>Note: Do not forget to release the original {@link #message}</b>
     *
     * @param privateKey message is signed with this key
     * @return the armed version of this envelope
     * @throws InvalidMessageFormatException if arming was not possible
     */
    public RemoteEnvelope<T> arm(final CompressedPrivateKey privateKey) throws InvalidMessageFormatException {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getPrivateHeader().writeDelimitedTo(outputStream);
            getBody().writeDelimitedTo(outputStream);

            final byte[] bytes = outputStream.toByteArray();

            // multicast messages will be signed only
            if (getRecipient() != null) {
                // FIXME: encrypt payload (message's id is currently not covered by signature and can
                //  therefore be forged. maybe we can prevent this by using the id as an initialisation
                //  vector for our encryption?)
                reverse(bytes);
            }

            // First encrypt and then sign. See: Krawczyk, Hugo. (2001). The Order of Encryption and Authentication for Protecting Communications (or: How Secure Is SSL?). 2139. 10.1007/3-540-44647-8_19.
            final byte[] signature = Crypto.signMessage(privateKey.toUncompressedKey(), bytes);
            final PublicHeader newPublicHeader = PublicHeader.newBuilder(getPublicHeader()).setSignature(ByteString.copyFrom(signature)).build();

            return of(newPublicHeader, bytes);
        }
        catch (final IOException | CryptoException e) {
            throw new InvalidMessageFormatException("Unable to arm message", e);
        }
    }

    /**
     * Returns an armed version of this envelope for sending it through untrustworthy channels.
     * <p>
     * {@code privateKey} must match message's sender.
     * <p>
     * Arming includes the following steps:
     * <ul>
     * <li>encrypted {@link #privateHeader} and encrypted {@link #body} will be signed with {@code privateKey}</li>
     * <li>{@link #privateHeader} and {@link #body} will be encrypted with recipient's public key (not implemented!)</li>
     * </ul>
     * <p>This method will release all resources even in case of an exception.
     *
     * @param privateKey message is signed with this key
     * @return the armed version of this envelope
     * @throws InvalidMessageFormatException if arming was not possible
     */
    public RemoteEnvelope<T> armAndRelease(final CompressedPrivateKey privateKey) throws InvalidMessageFormatException {
        try {
            return arm(privateKey);
        }
        finally {
            releaseAll();
        }
    }

    /**
     * Returns a disarmed version of this envelope.
     * <p>
     * {@code privateKey} must match message's recipient (not implemented yet!).
     * <p>
     * Disarming includes the following steps:
     * <ul>
     * <li>the encrypted {@link #privateHeader} and encrypted {@link #body} will be decrypted with {@code privateKey} (not implemented yet!)
     * <li>the signed portions of the message ({@link #privateHeader} and {@link #body}) are verified against sender's public key.
     * </ul>
     * <p>
     * <b>Note: Do not forget to release the original {@link #message}</b>
     *
     * @return the disarmed version of this envelope
     * @throws InvalidMessageFormatException if disarming was not possible
     */
    @SuppressWarnings({ "java:S1172" })
    public RemoteEnvelope<T> disarm(final CompressedPrivateKey privateKey) throws InvalidMessageFormatException {
        try {
            final PublicKey sender = getSender().toUncompressedKey();
            final byte[] signature = getPublicHeader().getSignature().toByteArray();
            try (final ByteBufInputStream in = new ByteBufInputStream(getOrBuildInternalByteBuf())) {
                final byte[] bytes = in.readAllBytes();

                // verify signature
                if (signature.length == 0) {
                    throw new InvalidMessageFormatException("No signature");
                }
                if (Crypto.verifySignature(sender, bytes, signature)) {
                    // multicast messages will be signed only
                    if (getRecipient() != null) {
                        // FIXME: decrypt payload
                        reverse(bytes);
                    }

                    try (final ByteArrayInputStream decryptedIn = new ByteArrayInputStream(bytes)) {
                        final PrivateHeader decryptedPrivateHeader = PrivateHeader.parseDelimitedFrom(decryptedIn);
                        final T decryptedBody = bodyFromInputStream(decryptedPrivateHeader.getType(), decryptedIn);

                        return of(getPublicHeader(), decryptedPrivateHeader, decryptedBody);
                    }
                }
                else {
                    throw new InvalidMessageFormatException("Invalid signature");
                }
            }
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Unable to disarm message", e);
        }
    }

    /**
     * Returns a disarmed version of this envelope.
     * <p>
     * {@code privateKey} must match message's recipient (not implemented yet!).
     * <p>
     * Disarming includes the following steps:
     * <ul>
     * <li>the encrypted {@link #privateHeader} and encrypted {@link #body} will be decrypted with {@code privateKey} (not implemented yet!)
     * <li>the signed portions of the message ({@link #privateHeader} and {@link #body}) are verified against sender's public key.
     * </ul>
     * <p>This method will release all resources even in case of an exception.
     *
     * @return the disarmed version of this envelope
     * @throws InvalidMessageFormatException if disarming was not possible
     */
    public RemoteEnvelope<T> disarmAndRelease(final CompressedPrivateKey privateKey) throws InvalidMessageFormatException {
        try {
            return disarm(privateKey);
        }
        finally {
            releaseAll();
        }
    }

    /**
     * Returns {@code true} if message is armed. Otherwise {@code false} is returned.
     *
     * @return {@code true} if message is armed. Otherwise {@code false} is returned.
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public boolean isArmed() throws InvalidMessageFormatException {
        return getSignature().length != 0;
    }

    /**
     * Returns {@code true} if message is not armed. Otherwise {@code false} is returned.
     *
     * @return {@code true} if message is not armed. Otherwise {@code false} is returned.
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public boolean isDisarmed() throws InvalidMessageFormatException {
        return !isArmed();
    }

    @SuppressWarnings({ "unchecked", "java:S1142" })
    private T bodyFromInputStream(final MessageType type,
                                  final InputStream in) throws InvalidMessageFormatException {
        try {
            switch (type) {
                case ACKNOWLEDGEMENT:
                    return (T) Acknowledgement.parseDelimitedFrom(in);
                case APPLICATION:
                    return (T) Application.parseDelimitedFrom(in);
                case UNITE:
                    return (T) Unite.parseDelimitedFrom(in);
                case DISCOVERY:
                    return (T) Discovery.parseDelimitedFrom(in);
                default:
                    throw new InvalidMessageFormatException("Message is not of any known type.");
            }
        }
        catch (final IOException e) {
            throw new InvalidMessageFormatException("Unable to read message body.", e);
        }
    }

    /**
     * This method is just a placeholder and only mimics the behavior of a real encryption simulate
     * (shift/replace bytes).
     *
     * @param bytes array of bytes what should be reversed
     */
    private static void reverse(final byte[] bytes) {
        if (bytes == null) {
            return;
        }
        int i = 0;
        int j = bytes.length - 1;
        byte tmp;
        while (j > i) {
            tmp = bytes[j];
            bytes[j] = bytes[i];
            bytes[i] = tmp;
            j--;
            i++;
        }
    }

    private ByteBuf proto2ByteBuf() throws InvalidMessageFormatException {
        final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();

        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            outputStream.write(MAGIC_NUMBER);
            publicHeader.writeDelimitedTo(outputStream);
            privateHeader.writeDelimitedTo(outputStream);
            body.writeDelimitedTo(outputStream);

            return byteBuf;
        }
        catch (final Exception e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            throw new InvalidMessageFormatException(e);
        }
    }

    /**
     * Creates new {@link Acknowledgement} message (reply to {@link Discovery} message).
     *
     * @param networkId       the network id of the node server
     * @param sender          the public key of the node server
     * @param proofOfWork     the proof of work of the node server
     * @param recipient       the public key of the recipient
     * @param correspondingId the corresponding id of the previous join message
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, {@code recipient}, or
     *                              {@code correspondingId} is {@code null}
     */
    public static RemoteEnvelope<Acknowledgement> acknowledgement(final int networkId,
                                                                  final CompressedPublicKey sender,
                                                                  final ProofOfWork proofOfWork,
                                                                  final CompressedPublicKey recipient,
                                                                  final MessageId correspondingId) {
        return of(
                buildPublicHeader(networkId, requireNonNull(sender), requireNonNull(proofOfWork), requireNonNull(recipient)),
                PrivateHeader.newBuilder().setType(ACKNOWLEDGEMENT).build(),
                Acknowledgement.newBuilder().setCorrespondingId(correspondingId.longValue()).build()
        );
    }

    static Protocol.PublicHeader buildPublicHeader(final int networkId,
                                                   final CompressedPublicKey sender,
                                                   final ProofOfWork proofOfWork,
                                                   final CompressedPublicKey recipient) {
        final PublicHeader.Builder builder = PublicHeader.newBuilder()
                .setId(randomMessageId().longValue())
                .setNetworkId(networkId)
                .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                .setProofOfWork(proofOfWork.intValue())
                .setHopCount(1);

        if (recipient != null) {
            builder.setRecipient(ByteString.copyFrom(recipient.byteArrayValue()));
        }

        return builder.build();
    }

    /**
     * Creates new {@link Application} message.
     *
     * @param networkId   the network the sender belongs to
     * @param sender      the sender
     * @param proofOfWork the sender's proof of work
     * @param recipient   the recipient
     * @param type        the payload type
     * @param payload     the data to be sent
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, {@code recipient}, or
     *                              {@code payload} is {@code null}
     */
    public static RemoteEnvelope<Application> application(final int networkId,
                                                          final CompressedPublicKey sender,
                                                          final ProofOfWork proofOfWork,
                                                          final CompressedPublicKey recipient,
                                                          final String type,
                                                          final byte[] payload) {
        final Application.Builder messageBuilder = Application.newBuilder();
        if (type != null) {
            messageBuilder.setType(type).setPayload(ByteString.copyFrom(payload));
        }

        return of(
                buildPublicHeader(networkId, requireNonNull(sender), requireNonNull(proofOfWork), requireNonNull(recipient)),
                PrivateHeader.newBuilder().setType(APPLICATION).build(),
                messageBuilder.build()
        );
    }

    /**
     * Creates a new {@link Discovery} message (sent by {@link InternetDiscovery}).
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @param recipient   the public key of the node to join
     * @param joinTime    if {@code 0} greater then 0, node will join a children.
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, or {@code recipient} is
     *                              {@code null}
     */
    public static RemoteEnvelope<Discovery> discovery(final int networkId,
                                                      final CompressedPublicKey sender,
                                                      final ProofOfWork proofOfWork,
                                                      final CompressedPublicKey recipient,
                                                      final long joinTime) {
        return of(
                buildPublicHeader(networkId, requireNonNull(sender), requireNonNull(proofOfWork), requireNonNull(recipient)),
                PrivateHeader.newBuilder()
                        .setType(DISCOVERY)
                        .build(), Discovery.newBuilder()
                        .setChildrenTime(joinTime)
                        .build()
        );
    }

    /**
     * Creates a new multicast {@link Discovery} message (sent by {@link
     * org.drasyl.remote.handler.LocalNetworkDiscovery}}.
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @throws NullPointerException if {@code sender}, or {@code proofOfWork} is {@code null}
     */
    public static RemoteEnvelope<Discovery> discovery(final int networkId,
                                                      final CompressedPublicKey sender,
                                                      final ProofOfWork proofOfWork) {
        return of(
                buildPublicHeader(networkId, requireNonNull(sender), requireNonNull(proofOfWork), null),
                PrivateHeader.newBuilder()
                        .setType(DISCOVERY)
                        .build(), Discovery.newBuilder()
                        .build()
        );
    }

    /**
     * Creates a new {@link Unite} message (sent by {@link org.drasyl.remote.handler.InternetDiscovery};
     * used for UDP hole punching}.
     *
     * @param networkId   the network of the super peer node
     * @param sender      the public key of the super peer node
     * @param proofOfWork the super peer's proof of work
     * @param recipient   the recipient of this message
     * @param publicKey   the public key of the node with which the receiver should unite
     * @param address     the {@code InetSocketAddress} of the node with which the receiver should
     *                    unite
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, {@code recipient},
     *                              {@code publicKey}, or {@code address} is {@code null}
     */
    public static RemoteEnvelope<Unite> unite(final int networkId,
                                              final CompressedPublicKey sender,
                                              final ProofOfWork proofOfWork,
                                              final CompressedPublicKey recipient,
                                              final CompressedPublicKey publicKey,
                                              final InetSocketAddress address) {
        return of(
                buildPublicHeader(networkId, requireNonNull(sender), requireNonNull(proofOfWork), requireNonNull(recipient)),
                PrivateHeader.newBuilder()
                        .setType(UNITE)
                        .build(), Unite.newBuilder()
                        .setPublicKey(ByteString.copyFrom(publicKey.byteArrayValue()))
                        .setAddress(address.getHostString())
                        .setPort(ByteString.copyFrom(UnsignedShort.of(address.getPort()).toBytes()))
                        .build()
        );
    }

    /**
     * Returns {@code true} if this message is a chunk. Otherwise {@code false} is returned.
     *
     * @return {@code true} if this message is a chunk. Otherwise {@code false}.
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public boolean isChunk() throws InvalidMessageFormatException {
        return getPublicHeader().getTotalChunks() > 0 || getPublicHeader().getChunkNo() > 0;
    }

    /**
     * Returns the number of the chunk. If the message is not a chunk, {@code 0} is returned.
     *
     * @return number of the chunk or {@code 0} if message is not a chunk
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public UnsignedShort getChunkNo() throws InvalidMessageFormatException {
        return UnsignedShort.of(getPublicHeader().getChunkNo());
    }

    /**
     * Returns the total chunks number. If the message is not a chunk, {@code 0} is returned.
     *
     * @return total chunks number or {@code 0} if message is not a chunk
     * @throws InvalidMessageFormatException if the public header cannot be read
     */
    public UnsignedShort getTotalChunks() throws InvalidMessageFormatException {
        return UnsignedShort.of(getPublicHeader().getTotalChunks());
    }

    public static byte[] magicNumber() {
        return MAGIC_NUMBER.clone();
    }
}
