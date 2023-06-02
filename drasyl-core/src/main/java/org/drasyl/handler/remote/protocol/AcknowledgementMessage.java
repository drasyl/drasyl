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
import org.drasyl.util.internal.Nullable;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.network.NetworkUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.drasyl.handler.remote.protocol.Nonce.randomNonce;
import static org.drasyl.handler.remote.protocol.PrivateHeader.MessageType.ACKNOWLEDGEMENT;

/**
 * Acknowledges a {@link HelloMessage}. The message's body is structured as follows:
 * <ul>
 * <li><b>Time</b>: The received {@link HelloMessage#getTime()} value this message is refers (8 bytes).</li>
 * <li><b>Endpoint</b>: UDP-port-IP-address-combination were the peer has received the corresponding {@link HelloMessage} from. IPv4 addresses will be mapped to IPv6 addresses (2 + 16 bytes)</li>
 * </ul>
 * <p>
 * This is an immutable object.
 */
@UnstableApi
@AutoValue
@SuppressWarnings("java:S118")
public abstract class AcknowledgementMessage extends AbstractFullReadMessage<AcknowledgementMessage> {
    public static final int MIN_LENGTH = 8;
    private static final int IPV6_LENGTH = 16;

    /**
     * Creates new acknowledgement message.
     *
     * @param hopCount    the hop count
     * @param isArmed     if the message is armed or not
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, or {@code correspondingId} is
     *                              {@code null}
     */
    @SuppressWarnings("java:S107")
    public static AcknowledgementMessage of(final HopCount hopCount,
                                            final boolean isArmed,
                                            final int networkId,
                                            final Nonce nonce,
                                            final DrasylAddress recipient,
                                            final DrasylAddress sender,
                                            final ProofOfWork proofOfWork,
                                            final long time,
                                            final InetSocketAddress endpoint) {
        return new AutoValue_AcknowledgementMessage(
                nonce,
                networkId,
                sender,
                proofOfWork,
                hopCount,
                isArmed,
                recipient,
                time,
                endpoint
        );
    }

    /**
     * Creates new acknowledgement message with random {@link Nonce}, and minimal {@link HopCount}.
     *
     * @param networkId   the network id
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException if {@code sender}, {@code proofOfWork}, {@code recipient}, or
     *                              {@code correspondingId} is {@code null}
     */
    public static AcknowledgementMessage of(final int networkId,
                                            final DrasylAddress recipient,
                                            final IdentityPublicKey sender,
                                            final ProofOfWork proofOfWork,
                                            final long time,
                                            final InetSocketAddress endpoint) {
        return of(
                HopCount.of(),
                false,
                networkId,
                randomNonce(),
                recipient,
                sender,
                proofOfWork,
                time,
                endpoint
        );
    }

    /**
     * Creates new acknowledgement message.
     *
     * @param hopCount    the hop count
     * @param networkId   the network id
     * @param nonce       the nonce
     * @param recipient   the public key of the recipient
     * @param sender      the public key of the sender
     * @param proofOfWork the proof of work of {@code sender}
     * @throws NullPointerException if {@code nonce},  {@code sender}, {@code proofOfWork}, {@code
     *                              recipient}, {@code hopCount}, {@code body}, or {@code
     *                              body.getCorrespondingId()} is {@code null}
     */
    @SuppressWarnings("java:S107")
    static AcknowledgementMessage of(final HopCount hopCount,
                                     final int networkId,
                                     final Nonce nonce,
                                     final DrasylAddress recipient,
                                     final DrasylAddress sender,
                                     final ProofOfWork proofOfWork,
                                     final ByteBuf body) throws InvalidMessageFormatException {
        if (body.readableBytes() < MIN_LENGTH) {
            throw new InvalidMessageFormatException("AcknowledgementMessage requires " + MIN_LENGTH + " readable bytes. Only " + body.readableBytes() + " left.");
        }

        final long time = body.readLong();
        final InetSocketAddress endpoint;
        if (body.readableBytes() >= 18) {
            try {
                final int port = body.readUnsignedShort();
                final byte[] addressBuffer = new byte[IPV6_LENGTH];
                body.readBytes(addressBuffer);
                final InetAddress address = InetAddress.getByAddress(addressBuffer);
                endpoint = new InetSocketAddress(address, port);
            }
            catch (final UnknownHostException e) {
                throw new InvalidMessageFormatException("Invalid private IP address.", e);
            }
        }
        else {
            endpoint = null;
        }

        return of(hopCount,
                false,
                networkId,
                nonce,
                recipient,
                sender,
                proofOfWork,
                time,
                endpoint
        );
    }

    /**
     * Returns the {@link HelloMessage#getTime()} value of the corresponding {@link HelloMessage}.
     */
    public abstract long getTime();

    /**
     * Returns the {@link InetSocketAddress} were the peer has received the corresponding {@link HelloMessage} from.
     */
    @Nullable
    public abstract InetSocketAddress getEndpoint();

    @Override
    public AcknowledgementMessage incrementHopCount() {
        return AcknowledgementMessage.of(getHopCount().increment(), getArmed(), getNetworkId(), getNonce(), getRecipient(), getSender(), getProofOfWork(), getTime(), getEndpoint());
    }

    @Override
    protected void writePrivateHeaderTo(final ByteBuf out) {
        PrivateHeader.of(ACKNOWLEDGEMENT, UnsignedShort.of(MIN_LENGTH)).writeTo(out);
    }

    @Override
    protected void writeBodyTo(final ByteBuf out) {
        out.writeLong(getTime());
        // endpoint parameter has be introduced with 0.11
        if (getEndpoint() != null) {
            out.writeShort(getEndpoint().getPort());
            out.writeBytes(NetworkUtil.getIpv4MappedIPv6AddressBytes(getEndpoint().getAddress()));
        }
    }

    @Override
    public int getLength() {
        int length = MAGIC_NUMBER_LEN + PublicHeader.LENGTH + PrivateHeader.LENGTH + MIN_LENGTH;
        // endpoint parameter has be introduced with 0.11
        if (getEndpoint() != null) {
            length += 2 + IPV6_LENGTH;
        }
        return length;
    }
}
