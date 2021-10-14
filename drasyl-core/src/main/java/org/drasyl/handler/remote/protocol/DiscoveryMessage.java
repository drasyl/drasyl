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
import org.drasyl.handler.remote.LocalNetworkDiscovery;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.UnsignedShort;

import static org.drasyl.handler.remote.protocol.Nonce.randomNonce;
import static org.drasyl.handler.remote.protocol.PrivateHeader.MessageType.DISCOVERY;

/*
 * Describes a method that is used to announce this node to peers or join a super node.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class DiscoveryMessage extends AbstractFullReadMessage<DiscoveryMessage> {
    public static final int LENGTH = 16;

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

    @Override
    public DiscoveryMessage incrementHopCount() {
        return DiscoveryMessage.of(getHopCount().increment(), getArmed(), getNetworkId(), getNonce(), getRecipient(), getSender(), getProofOfWork(), getTime(), getChildrenTime());
    }

    @Override
    protected void writePrivateHeaderTo(final ByteBuf out) {
        PrivateHeader.of(DISCOVERY, UnsignedShort.of(LENGTH)).writeTo(out);
    }

    @Override
    protected void writeBodyTo(final ByteBuf out) {
        out.writeLong(getTime());
        out.writeLong(getChildrenTime());
    }

    /**
     * Creates new application message.
     *
     * @param hopCount    the hop count
     * @param isArmed     if the message is armed or not
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param time
     * @param joinTime    the join time
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, or {@code hopCount} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static DiscoveryMessage of(final HopCount hopCount,
                                      final boolean isArmed,
                                      final int networkId,
                                      final Nonce nonce,
                                      final DrasylAddress recipient,
                                      final DrasylAddress sender,
                                      final ProofOfWork proofOfWork,
                                      final long time,
                                      final long joinTime) {
        return new AutoValue_DiscoveryMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                isArmed,
                recipient,
                time,
                joinTime
        );
    }

    /**
     * Creates a new {@link DiscoveryMessage} message.
     *
     * @param networkId    the network of the joining node
     * @param recipient    the public key of the node to join
     * @param sender       the public key of the joining node
     * @param proofOfWork  the proof of work
     * @param time         time in millis when this message was sent
     * @param childrenTime if {@code 0} greater then 0, node will join a children.
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, or {@code recipient} is
     *                              {@code null}
     */
    public static DiscoveryMessage of(final int networkId,
                                      final DrasylAddress recipient,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final long time,
                                      final long childrenTime) {
        return of(
                HopCount.of(), false, networkId, randomNonce(),
                recipient, sender,
                proofOfWork,
                time,
                childrenTime
        );
    }

    /**
     * Creates a new {@link DiscoveryMessage} message.
     *
     * @param networkId    the network of the joining node
     * @param recipient    the public key of the node to join
     * @param sender       the public key of the joining node
     * @param proofOfWork  the proof of work
     * @param childrenTime if {@code 0} greater then 0, node will join a children.
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, or {@code recipient} is
     *                              {@code null}
     */
    public static DiscoveryMessage of(final int networkId,
                                      final DrasylAddress recipient,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final long childrenTime) {
        return of(
                networkId,
                recipient,
                sender,
                proofOfWork,
                System.currentTimeMillis(),
                childrenTime
        );
    }

    /**
     * Creates a new multicast {@link DiscoveryMessage} message (sent by {@link
     * LocalNetworkDiscovery}}.
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @throws NullPointerException if {@code sender}, or {@code proofOfWork} is {@code null}
     */
    public static DiscoveryMessage of(final int networkId,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork) {
        return of(
                HopCount.of(), false, networkId, randomNonce(),
                null, sender,
                proofOfWork,
                System.currentTimeMillis(),
                0
        );
    }

    /**
     * Creates new discovery message.
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
    static DiscoveryMessage of(final HopCount hopCount,
                               final int networkId,
                               final Nonce nonce,
                               final DrasylAddress recipient,
                               final DrasylAddress sender,
                               final ProofOfWork proofOfWork,
                               final ByteBuf body) throws InvalidMessageFormatException {
        if (body.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("DiscoveryMessage requires " + LENGTH + " readable bytes. Only " + body.readableBytes() + " left.");
        }

        return of(
                hopCount, false, networkId, nonce,
                recipient, sender,
                proofOfWork,
                body.readLong(),
                body.readLong()
        );
    }
}
