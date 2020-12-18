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
package org.drasyl.remote.message;

import com.google.protobuf.ByteString;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.UnsignedShort;

import java.net.InetSocketAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;

/**
 * This message is sent to another node to inform them about the presence of a third node.
 */
public class UniteMessage extends AbstractMessage<Unite> {
    private final CompressedPublicKey publicKey;
    private final InetSocketAddress address;

    public UniteMessage(final int networkId,
                        final CompressedPublicKey sender,
                        final ProofOfWork proofOfWork,
                        final CompressedPublicKey recipient,
                        final CompressedPublicKey publicKey,
                        final InetSocketAddress address) {
        super(networkId, sender, proofOfWork, recipient);
        this.publicKey = requireNonNull(publicKey);
        this.address = requireNonNull(address);
    }

    public UniteMessage(final PublicHeader header,
                        final Unite body) throws Exception {
        super(header);
        this.publicKey = requireNonNull(CompressedPublicKey.of(body.getPublicKey().toByteArray()));
        this.address = new InetSocketAddress(body.getAddress(), UnsignedShort.of(body.getPort().toByteArray()).getValue());
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final UniteMessage that = (UniteMessage) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, address);
    }

    @Override
    public String toString() {
        return "UniteMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", signature=" + signature +
                ", publicKey=" + publicKey +
                ", address=" + address +
                ", id=" + id +
                '}';
    }

    @Override
    public PrivateHeader getPrivateHeader() {
        return PrivateHeader.newBuilder()
                .setType(UNITE)
                .build();
    }

    @Override
    public Unite getBody() {
        return Unite.newBuilder()
                .setPublicKey(ByteString.copyFrom(publicKey.byteArrayValue()))
                .setAddress(address.getHostString())
                .setPort(ByteString.copyFrom(UnsignedShort.of(address.getPort()).toBytes()))
                .build();
    }
}
