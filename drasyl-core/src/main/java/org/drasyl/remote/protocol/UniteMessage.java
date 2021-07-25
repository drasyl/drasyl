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
import com.google.common.primitives.Ints;
import com.google.protobuf.ByteString;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.Unite;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.drasyl.util.network.NetworkUtil.isValidPort;

/*
 * This message is sent by a super node for NAT traversal. The message provides routing information for a peer we want to directly communicate.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class UniteMessage extends AbstractFullReadMessage<UniteMessage> {
    /**
     * Returns the public key of the peer.
     *
     * @return the public key of the peer.
     */
    public abstract IdentityPublicKey getPublicKey();

    /**
     * Returns the ip address of the peer.
     *
     * @return the ip address of the peer.
     */
    public abstract InetAddress getAddress();

    /**
     * Returns the port of the peer.
     *
     * @return the port of the peer.
     */
    public abstract int getPort();

    @Override
    public UniteMessage incrementHopCount() {
        return UniteMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount().increment(), getAgreementId(), getPublicKey(), getAddress(), getPort());
    }

    @Override
    public UniteMessage setAgreementId(final AgreementId agreementId) {
        return UniteMessage.of(getNonce(), getNetworkId(), getSender(), getProofOfWork(), getRecipient(), getHopCount(), agreementId, getPublicKey(), getAddress(), getPort());
    }

    @Override
    protected PrivateHeader buildPrivateHeader() {
        return PrivateHeader.newBuilder()
                .setType(UNITE)
                .build();
    }

    @Override
    protected Unite buildBody() {
        final Unite.Builder builder = Unite.newBuilder()
                .setPublicKey(getPublicKey().getBytes())
                .setPort(getPort());

        if (getAddress() instanceof Inet4Address) {
            builder.setAddressV4(Ints.fromByteArray(getAddress().getAddress()));
        }
        else if (getAddress() instanceof Inet6Address) {
            builder.setAddressV6(ByteString.copyFrom(getAddress().getAddress()));
        }
        else {
            throw new IllegalArgumentException("address must be resolved");
        }

        return builder.build();
    }

    /**
     * Creates new unit message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @param publicKey   the public key of the peer
     * @param address     the ip address of the peer
     * @param port        the port of the peer
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code publicKey}, or
     *                                  {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code port} is invalid
     */
    @SuppressWarnings("java:S107")
    public static UniteMessage of(final Nonce nonce,
                                  final int networkId,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final IdentityPublicKey recipient,
                                  final HopCount hopCount,
                                  final AgreementId agreementId,
                                  final IdentityPublicKey publicKey,
                                  final InetAddress address,
                                  final int port) {
        if (port == 0 || !isValidPort(port)) {
            throw new IllegalArgumentException("invalid port");
        }

        return new AutoValue_UniteMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                agreementId,
                recipient,
                publicKey,
                address,
                port
        );
    }

    /**
     * Creates new unit message with random {@link Nonce}, minimal {@link HopCount} value, and no
     * {@link AgreementId}.
     *
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param publicKey   the public key of the peer
     * @param address     the ip address and port of the peer
     * @throws NullPointerException     if {@code sender}, {@code proofOfWork}, {@code recipient},
     *                                  {@code publicKey}, or {@code address.getAddress()} is {@code
     *                                  null}
     * @throws IllegalArgumentException if {@code address.getPort()} is invalid
     */
    public static UniteMessage of(final int networkId,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final IdentityPublicKey recipient,
                                  final IdentityPublicKey publicKey,
                                  final InetSocketAddress address) {
        return of(
                randomNonce(),
                networkId,
                sender,
                proofOfWork,
                recipient,
                HopCount.of(),
                null,
                publicKey,
                address.getAddress(),
                address.getPort()
        );
    }

    /**
     * Creates new unit message.
     *
     * @param nonce       the nonce
     * @param networkId   the network id
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param recipient   the public key of the recipient
     * @param hopCount    the hop count
     * @param agreementId the agreement id
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code publicKey}, or
     *                                  {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code body} contains an invalid address
     */
    @SuppressWarnings("java:S107")
    static UniteMessage of(final Nonce nonce,
                           final int networkId,
                           final IdentityPublicKey sender,
                           final ProofOfWork proofOfWork,
                           final IdentityPublicKey recipient,
                           final HopCount hopCount,
                           final AgreementId agreementId,
                           final Unite body) {
        final InetAddress address;
        try {
            if (body.getAddressV6().isEmpty()) {
                address = InetAddress.getByAddress(Ints.toByteArray(body.getAddressV4()));
            }
            else {
                address = InetAddress.getByAddress(body.getAddressV6().toByteArray());
            }

            return of(
                    nonce,
                    networkId,
                    sender,
                    proofOfWork,
                    recipient,
                    hopCount,
                    agreementId,
                    IdentityPublicKey.of(body.getPublicKey()),
                    address,
                    body.getPort()
            );
        }
        catch (final UnknownHostException e) {
            throw new IllegalArgumentException("address is of illegal length.", e);
        }
    }
}
