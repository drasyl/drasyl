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
import org.drasyl.annotation.Nullable;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.InternetDiscovery;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;

import java.io.IOException;
import java.io.OutputStream;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;

/*
 * Describes a method that is used to announce this node to peers or join a super node.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class DiscoveryMessage extends AbstractFullReadMessage<DiscoveryMessage> {
    /**
     * Returns the {@link IdentityPublicKey} of the message recipient. If the message has no
     * recipient (e.g. because it is a multicast message) {@code null} is returned.
     */
    @Nullable
    public abstract IdentityPublicKey getRecipient();

    /**
     * If the value is greater than {@code 0}, it indicates that this node wants to join the
     * receiver as a child. For this, the receiver must be configured as a super node. In all other
     * cases, the message is used to announce this node's presence to the recipient.
     */
    public abstract long getChildrenTime();

    @Override
    public DiscoveryMessage incrementHopCount() {
        return DiscoveryMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getAgreementId(), getChildrenTime());
    }

    @Override
    public DiscoveryMessage setAgreementId(final AgreementId agreementId) {
        return DiscoveryMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount(), agreementId, getChildrenTime());
    }

    @Override
    protected void writePrivateHeaderTo(final OutputStream out) throws IOException {
        PrivateHeader.newBuilder()
                .setType(DISCOVERY)
                .build()
                .writeDelimitedTo(out);
    }

    @Override
    protected void writeBodyTo(final OutputStream out) throws IOException {
        Discovery.newBuilder()
                .setChildrenTime(getChildrenTime())
                .build()
                .writeDelimitedTo(out);
    }

    /**
     * Creates new application message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @param joinTime    the join time
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, or {@code hopCount} is {@code null}
     */
    @SuppressWarnings("java:S107")
    public static DiscoveryMessage of(final Nonce nonce,
                                      final int networkId,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final IdentityPublicKey recipient,
                                      final HopCount hopCount,
                                      final AgreementId agreementId,
                                      final long joinTime) {
        return new AutoValue_DiscoveryMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                agreementId,
                recipient,
                joinTime
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
    public static DiscoveryMessage of(final int networkId,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final IdentityPublicKey recipient,
                                      final long joinTime) {
        return of(
                randomNonce(),
                networkId,
                sender,
                proofOfWork,
                recipient,
                HopCount.of(),
                null,
                joinTime
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
    public static DiscoveryMessage of(final int networkId,
                                      final IdentityPublicKey sender,
                                      final ProofOfWork proofOfWork) {
        return of(
                randomNonce(),
                networkId,
                sender,
                proofOfWork,
                null,
                HopCount.of(),
                null,
                0
        );
    }

    /**
     * Creates new discovery message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code body}, or {@code
     *                                  body.getChildrenTime()}
     * @throws IllegalArgumentException if {@code body} contains an invalid address
     */
    @SuppressWarnings("java:S107")
    static DiscoveryMessage of(final Nonce nonce,
                               final int networkId,
                               final IdentityPublicKey sender,
                               final ProofOfWork proofOfWork,
                               final IdentityPublicKey recipient,
                               final HopCount hopCount,
                               final AgreementId agreementId,
                               final Discovery body) {
        return of(
                nonce,
                networkId,
                sender,
                proofOfWork,
                recipient,
                hopCount,
                agreementId,
                body.getChildrenTime()
        );
    }
}
