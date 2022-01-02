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
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.UnsignedShort;
import org.drasyl.util.network.NetworkUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.drasyl.handler.remote.protocol.PrivateHeader.MessageType.UNITE;

/**
 * This message is sent by a super node for NAT traversal. The message provides routing information
 * for a peer we want to directly communicate. The message's body is structured as follows:
 * <ul>
 * <li><b>Address</b>: The {@link DrasylAddress} to which this message contains routing information (32 bytes).</li>
 * <li><b>Port</b>: The UDP port through which the node can be reached (2 bytes).</li>
 * <li><b>InetAddress</b>: The IP address which the node can be reached. IPv4 addresses will be mapped to IPv6 addresses (16 bytes).</li>
 * </ul>
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class UniteMessage extends AbstractFullReadMessage<UniteMessage> {
    public static final int LENGTH = 50;
    private static final int IPV6_LENGTH = 16;

    /**
     * Creates new unit message.
     *
     * @param hopCount    the hop count
     * @param isArmed     if the message is armed or not
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param address     the public key of the peer
     * @param inetAddress the ip address of the peer
     * @param port        the port of the peer
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code address}, or
     *                                  {@code inetAddress} is {@code null}
     * @throws IllegalArgumentException if {@code port} is invalid
     */
    @SuppressWarnings("java:S107")
    public static UniteMessage of(final HopCount hopCount,
                                  final boolean isArmed,
                                  final int networkId,
                                  final Nonce nonce,
                                  final DrasylAddress recipient,
                                  final DrasylAddress sender,
                                  final ProofOfWork proofOfWork,
                                  final DrasylAddress address,
                                  final InetAddress inetAddress,
                                  final UnsignedShort port) {
        if (port.getValue() == 0) {
            throw new IllegalArgumentException("invalid port");
        }

        return new AutoValue_UniteMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                isArmed,
                recipient,
                address,
                inetAddress,
                port
        );
    }

    /**
     * Creates new unit message with random {@link Nonce}, and minimal {@link HopCount} value.
     *
     * @param networkId   the network id
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @param address     the public key of the peer
     * @param inetAddress the ip inetAddress and port of the peer
     * @throws NullPointerException     if {@code sender}, {@code proofOfWork}, {@code recipient},
     *                                  {@code address}, or {@code inetAddress.getAddress()} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code inetAddress.getPort()} is invalid
     */
    public static UniteMessage of(final int networkId,
                                  final DrasylAddress recipient,
                                  final IdentityPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final DrasylAddress address,
                                  final InetSocketAddress inetAddress) {
        return of(
                HopCount.of(), false, networkId, Nonce.randomNonce(),
                recipient,
                sender,
                proofOfWork,
                address,
                inetAddress.getAddress(),
                UnsignedShort.of(inetAddress.getPort())
        );
    }

    /**
     * Creates new unite message.
     *
     * @param hopCount    the hop count
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException     if {@code nonce},  {@code sender}, {@code proofOfWork},
     *                                  {@code recipient}, {@code hopCount}, {@code publicKey},
     *                                  {@code body}, or {@code address} is {@code null}
     * @throws IllegalArgumentException if {@code body} contains an invalid address
     */
    @SuppressWarnings("java:S107")
    static UniteMessage of(final HopCount hopCount,
                           final int networkId,
                           final Nonce nonce,
                           final DrasylAddress recipient,
                           final DrasylAddress sender,
                           final ProofOfWork proofOfWork,
                           final ByteBuf body) throws InvalidMessageFormatException {
        if (body.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("UniteMessage requires " + LENGTH + " readable bytes. Only " + body.readableBytes() + " left.");
        }
        final byte[] pubkeyBuffer = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
        body.readBytes(pubkeyBuffer);

        final UnsignedShort port = UnsignedShort.of(body.readUnsignedShort());

        final byte[] addressBuffer = new byte[IPV6_LENGTH];
        body.readBytes(addressBuffer);

        try {
            return of(
                    hopCount, false, networkId, nonce,
                    recipient, sender,
                    proofOfWork,
                    IdentityPublicKey.of(pubkeyBuffer),
                    InetAddress.getByAddress(addressBuffer),
                    port
            );
        }
        catch (final UnknownHostException e) {
            throw new IllegalArgumentException("address is of illegal length.", e);
        }
    }

    /**
     * Returns the public key of the peer.
     *
     * @return the public key of the peer.
     */
    public abstract DrasylAddress getAddress();

    /**
     * Returns the ip address of the peer.
     *
     * @return the ip address of the peer.
     */
    public abstract InetAddress getInetAddress();

    /**
     * Returns the port of the peer.
     *
     * @return the port of the peer.
     */
    public abstract UnsignedShort getPort();

    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(getInetAddress(), getPort().getValue());
    }

    @Override
    public UniteMessage incrementHopCount() {
        return UniteMessage.of(getHopCount().increment(), getArmed(), getNetworkId(), getNonce(), getRecipient(), getSender(), getProofOfWork(), getAddress(), getInetAddress(), getPort());
    }

    @Override
    protected void writePrivateHeaderTo(final ByteBuf out) {
        PrivateHeader.of(UNITE, UnsignedShort.of(LENGTH)).writeTo(out);
    }

    @Override
    protected void writeBodyTo(final ByteBuf out) {
        out.writeBytes(getAddress().toByteArray())
                .writeBytes(getPort().toBytes())
                .writeBytes(NetworkUtil.getIpv4MappedIPv6AddressBytes(getInetAddress()));
    }
}
