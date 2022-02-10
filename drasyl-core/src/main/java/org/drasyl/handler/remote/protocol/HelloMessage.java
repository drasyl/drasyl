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
import org.drasyl.annotation.Nullable;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.handler.remote.LocalNetworkDiscovery;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.ArrayUtil;
import org.drasyl.util.ImmutableByteArray;
import org.drasyl.util.UnsignedShort;

import java.nio.ByteBuffer;

import static org.drasyl.crypto.sodium.DrasylSodiumWrapper.SIGN_BYTES;
import static org.drasyl.handler.remote.protocol.Nonce.randomNonce;
import static org.drasyl.handler.remote.protocol.PrivateHeader.MessageType.HELLO;

/**
 * Describes a message that is used to announce this node to peers or to join a super node. The
 * message's body is structured as follows:
 * <ul>
 * <li><b>Time</b>: The sender's current time in milliseconds stored in 8 bytes.</li>
 * <li><b>ChildrenTime</b>: Specifies how many seconds (8 bytes) the sender wants to join the receiving super peer. If the value is 0, the message is an announcement and not a join.</li>
 * <li><b>Signature</b>: 64 byte signature. Only present if ChildrenTime is > 0.</li>
 * </ul>
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class HelloMessage extends AbstractFullReadMessage<HelloMessage> {
    public static final int UNSIGNED_LENGTH = 16;
    public static final int SIGNED_LENGTH = UNSIGNED_LENGTH + SIGN_BYTES;

    /**
     * Creates new application message.
     *
     * @param hopCount     the hop count
     * @param isArmed      if the message is armed or not
     * @param networkId    the network id
     * @param nonce        the nonce
     * @param recipient    the public key of the recipient
     * @param sender       the public key of the sender
     * @param proofOfWork  the proof of work of {@code sender}
     * @param time
     * @param childrenTime the join time
     * @param signature
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, or {@code hopCount} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static HelloMessage of(final HopCount hopCount,
                                  final boolean isArmed,
                                  final int networkId,
                                  final Nonce nonce,
                                  final DrasylAddress recipient,
                                  final DrasylAddress sender,
                                  final ProofOfWork proofOfWork,
                                  final long time,
                                  final long childrenTime,
                                  final ImmutableByteArray signature) {
        return new AutoValue_HelloMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                isArmed,
                recipient,
                time,
                childrenTime,
                signature
        );
    }

    /**
     * Creates new application message.
     *
     * @param hopCount     the hop count
     * @param isArmed      if the message is armed or not
     * @param networkId    the network id
     * @param nonce        the nonce
     * @param recipient    the public key of the recipient
     * @param sender       the public key of the sender
     * @param proofOfWork  the proof of work of {@code sender}
     * @param time
     * @param childrenTime the join time
     * @param signature
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, or {@code hopCount} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static HelloMessage of(final HopCount hopCount,
                                  final boolean isArmed,
                                  final int networkId,
                                  final Nonce nonce,
                                  final DrasylAddress recipient,
                                  final DrasylAddress sender,
                                  final ProofOfWork proofOfWork,
                                  final long time,
                                  final long childrenTime,
                                  final IdentitySecretKey secretKey) {
        try {
            final byte[] signature;
            if (childrenTime > 0) {
                // create signature that covers the recipient, sender, time, and children time
                final byte[] message = ArrayUtil.concat(
                        recipient.toByteArray(),
                        sender.toByteArray(),
                        ByteBuffer.allocate(Long.BYTES * 2).putLong(time).putLong(childrenTime).array()
                );
                signature = Crypto.INSTANCE.sign(message, secretKey);
            }
            else {
                signature = new byte[0];
            }

            return new AutoValue_HelloMessage(
                    nonce,
                    networkId,
                    sender,
                    proofOfWork,
                    hopCount,
                    isArmed,
                    recipient,
                    time,
                    childrenTime,
                    ImmutableByteArray.of(signature)
            );
        }
        catch (final CryptoException e) {
            throw new IllegalArgumentException("Unable to sign message:", e);
        }
    }

    /**
     * Creates a new {@link HelloMessage} message.
     *
     * @param networkId    the network of the joining node
     * @param recipient    the public key of the node to join
     * @param sender       the public key of the joining node
     * @param proofOfWork  the proof of work
     * @param time         time in millis when this message was sent
     * @param childrenTime if {@code 0} greater then 0, node will join a children.
     * @param secretKey    the secret key used to sign this message
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, or {@code recipient} is
     *                              {@code null}
     */
    public static HelloMessage of(final int networkId,
                                  final DrasylAddress recipient,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final long time,
                                  final long childrenTime,
                                  final IdentitySecretKey secretKey) {
        return of(
                HopCount.of(), false, networkId, randomNonce(),
                recipient, sender,
                proofOfWork,
                time,
                childrenTime,
                secretKey
        );
    }

    /**
     * Creates a new {@link HelloMessage} message.
     *
     * @param networkId    the network of the joining node
     * @param recipient    the public key of the node to join
     * @param sender       the public key of the joining node
     * @param proofOfWork  the proof of work
     * @param childrenTime if {@code 0} greater then 0, node will join a children.
     * @param secretKey    the secret key used to sign this message
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, or {@code recipient} is
     *                              {@code null}
     */
    public static HelloMessage of(final int networkId,
                                  final DrasylAddress recipient,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final long childrenTime,
                                  final IdentitySecretKey secretKey) {
        return of(
                networkId,
                recipient,
                sender,
                proofOfWork,
                System.currentTimeMillis(),
                childrenTime,
                secretKey);
    }

    /**
     * Creates a new {@link HelloMessage} message.
     *
     * @param networkId   the network of the joining node
     * @param recipient   the public key of the node to join
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, or {@code recipient} is
     *                              {@code null}
     */
    public static HelloMessage of(final int networkId,
                                  final DrasylAddress recipient,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork) {
        return of(
                networkId,
                recipient,
                sender,
                proofOfWork,
                System.currentTimeMillis(),
                0,
                null);
    }

    /**
     * Creates a new multicast {@link HelloMessage} message (sent by {@link
     * LocalNetworkDiscovery}}.
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @throws NullPointerException if {@code sender}, or {@code proofOfWork} is {@code null}
     */
    public static HelloMessage of(final int networkId,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork) {
        return of(
                HopCount.of(), false, networkId, randomNonce(),
                null, sender,
                proofOfWork,
                System.currentTimeMillis(),
                0,
                ImmutableByteArray.of(new byte[0])
        );
    }

    /**
     * Creates new hello message.
     *
     * @param hopCount    the hop count
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code body}, or {@code
     *                                  body.getChildrenTime()}
     * @throws IllegalArgumentException if {@code body} contains an invalid address
     */
    @SuppressWarnings("java:S107")
    static HelloMessage of(final HopCount hopCount,
                           final int networkId,
                           final Nonce nonce,
                           final DrasylAddress recipient,
                           final DrasylAddress sender,
                           final ProofOfWork proofOfWork,
                           final ByteBuf body) throws InvalidMessageFormatException {
        if (body.readableBytes() < UNSIGNED_LENGTH) {
            throw new InvalidMessageFormatException("DiscoveryMessage requires " + UNSIGNED_LENGTH + " readable bytes. Only " + body.readableBytes() + " left.");
        }

        final long time = body.readLong();
        final long childrenTime = body.readLong();
        final byte[] signature;
        // for backward compatibility reasons, the signature is currently optional
        if (childrenTime > 0 && body.readableBytes() >= SIGN_BYTES) {
            signature = new byte[SIGN_BYTES];
            body.readBytes(signature);
        }
        else {
            signature = new byte[0];
        }

        return of(
                hopCount, false, networkId, nonce,
                recipient, sender,
                proofOfWork,
                time,
                childrenTime,
                ImmutableByteArray.of(signature)
        );
    }

    /**
     * Returns the {@link IdentityPublicKey} of the message recipient. If the message has no
     * recipient (e.g. because it is a multicast message) {@code null} is returned.
     *
     * @return
     */
    @Nullable
    public abstract DrasylAddress getRecipient();

    /**
     * Returns the time this message has been sent.
     */
    public abstract long getTime();

    /**
     * If the value is greater than {@code 0}, it indicates that this node wants to join the
     * receiver as a child. For this, the receiver must be configured as a super node. In all other
     * cases, the message is used to announce this node's presence to the recipient.
     */
    public abstract long getChildrenTime();

    public abstract ImmutableByteArray getSignature();

    @Override
    public HelloMessage incrementHopCount() {
        return HelloMessage.of(getHopCount().increment(), getArmed(), getNetworkId(), getNonce(), getRecipient(), getSender(), getProofOfWork(), getTime(), getChildrenTime(), getSignature());
    }

    /**
     * Returns {@code true} if message is signed. This message will <strong>not</strong> verifiy the
     * signature! Use {@link #verifySignature()} to check if supplied signature is valid.
     *
     * @return {@code true} if message is signed
     */
    public boolean isSigned() {
        return !getSignature().isEmpty();
    }

    /**
     * Returns {@code true} if message is signed and the signature is valid. Use {@link #isSigned()}
     * to check whether a message is signed.
     *
     * @return {@code true} if message is signed and the signature is valid
     */
    public boolean verifySignature() {
        final byte[] message = signedAttributes(getRecipient(), getSender(), getTime(), getChildrenTime());
        return Crypto.INSTANCE.verifySignature(getSignature().getArray(), message, (IdentityPublicKey) getSender());
    }

    @Override
    protected void writePrivateHeaderTo(final ByteBuf out) {
        PrivateHeader.of(HELLO, UnsignedShort.of(getChildrenTime() > 0 ? SIGNED_LENGTH : UNSIGNED_LENGTH)).writeTo(out);
    }

    @Override
    protected void writeBodyTo(final ByteBuf out) {
        out.writeLong(getTime());
        out.writeLong(getChildrenTime());
        out.writeBytes(getSignature().getArray());
    }

    private static byte[] signedAttributes(final DrasylAddress recipient,
                                           final DrasylAddress sender,
                                           final long time,
                                           final long childrenTime) {
        return ArrayUtil.concat(
                recipient.toByteArray(),
                sender.toByteArray(),
                ByteBuffer.allocate(Long.BYTES * 2).putLong(time).putLong(childrenTime).array()
        );
    }
}
