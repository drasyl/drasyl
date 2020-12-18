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

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.message.MessageId;
import org.drasyl.remote.message.UserAgent;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IntermediateEnvelopeTest {
    private ByteBuf message;
    private PublicHeader header;
    private PrivateHeader privateHeader;
    private Application body;
    private int publicHeaderLength;
    private int privateHeaderLength;
    private int bodyLength;

    @BeforeEach
    void setUp() throws IOException, CryptoException {
        header = PublicHeader.newBuilder()
                .setId(ByteString.copyFrom(MessageId.of("412176952b5b81fd13f84a7c").byteArrayValue()))
                .setUserAgent(ByteString.copyFrom(UserAgent.generate().getVersion().toBytes()))
                .setNetworkId(1)
                .setSender(ByteString.copyFrom(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9").byteArrayValue()))
                .setProofOfWork(ProofOfWork.of(6657650).intValue())
                .setRecipient(ByteString.copyFrom(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3").byteArrayValue()))
                .build();

        privateHeader = PrivateHeader.newBuilder()
                .setType(APPLICATION)
                .build();

        body = Application.newBuilder()
                .setPayload(ByteString.copyFrom("Lorem ipsum dolor sit amet".getBytes())).build();

        message = Unpooled.buffer();
        final ByteBufOutputStream outputStream = new ByteBufOutputStream(message);
        header.writeDelimitedTo(outputStream);
        publicHeaderLength = outputStream.writtenBytes();
        privateHeader.writeDelimitedTo(outputStream);
        privateHeaderLength = outputStream.writtenBytes() - publicHeaderLength;
        body.writeDelimitedTo(outputStream);
        bodyLength = outputStream.writtenBytes() - publicHeaderLength - privateHeaderLength;
        outputStream.close();
    }

    @Test
    void shouldOnlyReturnHeaderAndNotChangingTheUnderlyingByteBuf() throws IOException {
        try {
            final byte[] backedByte = message.array();
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals(header, envelope.getPublicHeader());
            assertEquals((privateHeaderLength + bodyLength), envelope.getInternalByteBuf().readableBytes());
            assertEquals(backedByte, envelope.getInternalByteBuf().array());
            assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.getByteBuf().readableBytes());
            assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
        }
        finally {
            message.release();
        }
    }

    @Test
    void shouldOnlyReturnPrivateHeaderButReadAlsoPublicHeader() throws IOException {
        try {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals(privateHeader, envelope.getPrivateHeader());
            assertEquals(bodyLength, envelope.getInternalByteBuf().readableBytes());
            assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.getByteBuf().readableBytes());
            assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
        }
        finally {
            message.release();
        }
    }

    @Test
    void shouldOnlyReturnBodyButReadAll() throws IOException {
        try {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals(body, envelope.getBody());
            assertEquals(0, envelope.getInternalByteBuf().readableBytes());
            assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.getByteBuf().readableBytes());
            assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
        }
        finally {
            message.release();
        }
    }
}