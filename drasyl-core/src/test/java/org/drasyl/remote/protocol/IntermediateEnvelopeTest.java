/*
 * Copyright (c) 2020-2021.
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntermediateEnvelopeTest {
    private CompressedPublicKey senderPublicKey;
    private CompressedPrivateKey senderPrivateKey;
    private CompressedPublicKey recipientPublicKey;
    private CompressedPrivateKey recipientPrivateKey;
    private ByteBuf message;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private Application body;
    private int publicHeaderLength;
    private int privateHeaderLength;
    private int bodyLength;
    private MessageId messageId;
    private ProofOfWork senderProofOfWork;

    @BeforeEach
    void setUp() throws IOException {
        senderPublicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
        senderPrivateKey = CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d");
        recipientPublicKey = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        recipientPrivateKey = CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34");
        messageId = MessageId.of("412176952b5b81fd");
        senderProofOfWork = ProofOfWork.of(6657650);
        publicHeader = PublicHeader.newBuilder()
                .setId(messageId.longValue())
                .setNetworkId(1)
                .setSender(ByteString.copyFrom(senderPublicKey.byteArrayValue()))
                .setProofOfWork(senderProofOfWork.intValue())
                .setRecipient(ByteString.copyFrom(recipientPublicKey.byteArrayValue()))
                .setHopCount(1)
                .build();

        privateHeader = PrivateHeader.newBuilder()
                .setType(APPLICATION)
                .build();

        body = Application.newBuilder()
                .setPayload(ByteString.copyFrom("Lorem ipsum dolor sit amet".getBytes())).build();

        message = Unpooled.buffer();
        try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(message)) {
            outputStream.write(IntermediateEnvelope.magicNumber());
            publicHeader.writeDelimitedTo(outputStream);
            publicHeaderLength = outputStream.writtenBytes();
            privateHeader.writeDelimitedTo(outputStream);
            privateHeaderLength = outputStream.writtenBytes() - publicHeaderLength;
            body.writeDelimitedTo(outputStream);
            bodyLength = outputStream.writtenBytes() - publicHeaderLength - privateHeaderLength;
        }
    }

    @AfterEach
    void tearDown() {
        ReferenceCountUtil.safeRelease(message);
    }

    @Nested
    class Of {
        @Nested
        class WithByteBuf {
            @Test
            void shouldOnlyReturnHeaderAndNotChangingTheUnderlyingByteBuf() throws IOException {
                final byte[] backedByte = message.array();
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(publicHeader, envelope.getPublicHeader());
                assertEquals((privateHeaderLength + bodyLength), envelope.getInternalByteBuf().readableBytes());
                assertEquals(backedByte, envelope.getInternalByteBuf().array());
                assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.copy().readableBytes());
                assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
            }

            @Test
            void shouldOnlyReturnPrivateHeaderButReadAlsoPublicHeader() throws IOException {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(privateHeader, envelope.getPrivateHeader());
                assertEquals(bodyLength, envelope.getInternalByteBuf().readableBytes());
                assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.copy().readableBytes());
                assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
            }

            @Test
            void shouldOnlyReturnBodyButReadAll() throws IOException {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(body, envelope.getBody());
                assertEquals(0, envelope.getInternalByteBuf().readableBytes());
                assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.copy().readableBytes());
                assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
            }

            @Test
            void shouldBuildByteBufOnMissingByteBuf() throws IOException {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertNotNull(envelope.copy());
                assertNotNull(envelope.getInternalByteBuf());
                assertNotNull(envelope.getBodyAndRelease());

                assertNull(envelope.copy());
                assertNull(envelope.getInternalByteBuf());

                assertNotNull(envelope.getOrBuildInternalByteBuf());
                assertNotNull(envelope.copy());
                assertNotNull(envelope.getInternalByteBuf());
                assertNotNull(envelope.getBodyAndRelease());
            }

            @Test
            void shouldShareRefCnt() throws IOException {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                final ByteBuf original = envelope.getInternalByteBuf();
                final ByteBuf copy = envelope.copy();

                assertEquals(original.refCnt(), copy.refCnt());
                assertEquals(1, original.refCnt());
                assertEquals(1, copy.refCnt());
                envelope.retain();
                assertEquals(original.refCnt(), copy.refCnt());
                assertEquals(2, original.refCnt());
                assertEquals(2, copy.refCnt());
                envelope.releaseAll();
            }

            @Test
            void shouldThrowExceptionForNonReadableByteBuf(@Mock final ByteBuf message) {
                when(message.refCnt()).thenReturn(1);

                assertThrows(IOException.class, () -> IntermediateEnvelope.of(message));
            }
        }
    }

    @Nested
    class ReferenceCounted {
        @Mock
        CompositeByteBuf message;
        MessageLite body;
        IntermediateEnvelope<MessageLite> underTest;

        @BeforeEach
        void setUp() {
            underTest = new IntermediateEnvelope<>(message, publicHeader, privateHeader, body);
        }

        @Test
        void refCntShouldBeCalledOnOriginalMessage() {
            underTest.refCnt();

            verify(message).refCnt();
        }

        @Test
        void retainShouldBeCalledOnOriginalMessage() {
            underTest.retain();

            verify(message).retain();
        }

        @Test
        void retainWithIncrementShouldBeCalledOnOriginalMessage() {
            underTest.retain(1337);

            verify(message).retain(1337);
        }

        @Test
        void touchShouldBeCalledOnOriginalMessage() {
            underTest.touch();

            verify(message).touch();
        }

        @Test
        void touchWithHintShouldBeCalledOnOriginalMessage(@Mock final Object hint) {
            underTest.touch(hint);

            verify(message).touch(hint);
        }

        @Test
        void releaseShouldBeCalledOnOriginalMessage() {
            when(underTest.refCnt()).thenReturn(1);
            underTest.release();

            verify(message).release();
        }

        @Test
        void releaseWithDecrementShouldBeCalledOnOriginalMessage() {
            underTest.release(1337);

            verify(message).release(1337);
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldNotFail() throws IOException {
            try (final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message)) {
                assertNotNull(envelope.toString());
            }
        }
    }

    @Nested
    class GetId {
        @Test
        void shouldReturnId() throws IOException {
            try (final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message)) {
                assertEquals(messageId, envelope.getId());
            }
        }

        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getId);
        }
    }

    @Nested
    class GetNetworkId {
        @Test
        void shouldReturnNetworkId() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals(1, envelope.getNetworkId());
        }

        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getNetworkId);
        }
    }

    @Nested
    class GetProofOfWork {
        @Test
        void shouldReturnProofOfWork() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals(ProofOfWork.of(6657650), envelope.getProofOfWork());
        }

        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getProofOfWork);
        }
    }

    @Nested
    class GetRecipient {
        @Test
        void shouldReturnRecipient() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals(recipientPublicKey, envelope.getRecipient());
        }

        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getRecipient);
        }
    }

    @Nested
    class GetSender {
        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getSender);
        }
    }

    @Nested
    class GetHopCount {
        @Test
        void shouldReturnHopCount() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertEquals((byte) 0, envelope.getHopCount());
        }

        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getHopCount);
        }
    }

    @Nested
    class IncrementHopCount {
        @Test
        void shouldIncrementIfMessageIsPresentOnlyInByteBuf() throws IOException {
            final CompositeByteBuf message = Unpooled.compositeBuffer().addComponent(true, Unpooled.wrappedBuffer(HexUtil.fromString("1e3f500156099c3495a5f68386571a21030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22209cdc9b062a21030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f223001020801100a0a48616c6c6f2057656c7412025b42")));
            try (final IntermediateEnvelope<Application> envelope = new IntermediateEnvelope<>(message, null, null, null)) {
                envelope.incrementHopCount();

                assertEquals(1, envelope.getHopCount());
                assertEquals(1, IntermediateEnvelope.of(envelope.getOrBuildByteBuf()).getHopCount());
            }
        }

        @Test
        void shouldIncrementIfMessageIsPresentInByteBufAndEnvelope() throws IOException {
            final CompositeByteBuf message = Unpooled.compositeBuffer().addComponent(true, Unpooled.wrappedBuffer(HexUtil.fromString("1e3f500156099c3495a5f68386571a21030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22209cdc9b062a21030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f223001020801100a0a48616c6c6f2057656c7412025b42")));
            final PublicHeader publicHeader = IntermediateEnvelope.buildPublicHeader(0, CompressedPublicKey.of("1e3f50015609fc450176d19fd6192221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22289cdc9b063221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f2238021e3f50015c0a085672b26b94d530ef120200002221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22289cdc9b063221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f223a0100020801100a0a48616c6c6f2057656c7412025b42"), ProofOfWork.of(6518542), CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"));
            final PrivateHeader privateHeader = PrivateHeader.newBuilder()
                    .setType(APPLICATION)
                    .build();
            final Application body = Application.newBuilder()
                    .setType(byte[].class.getName())
                    .setPayload(ByteString.copyFrom("Hallo Welt".getBytes()))
                    .build();
            try (final IntermediateEnvelope<Application> envelope = new IntermediateEnvelope<>(message, publicHeader, privateHeader, body)) {
                envelope.incrementHopCount();

                assertEquals(1, envelope.getHopCount());
                assertEquals(1, IntermediateEnvelope.of(envelope.getOrBuildByteBuf()).getHopCount());
            }
        }

        @Test
        void shouldIncrementIfMessageIsPresentOnlyInEnvelope() throws IOException {
            final PublicHeader publicHeader = IntermediateEnvelope.buildPublicHeader(0, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), ProofOfWork.of(6518542), CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"));
            final PrivateHeader privateHeader = PrivateHeader.newBuilder()
                    .setType(APPLICATION)
                    .build();
            final Application body = Application.newBuilder()
                    .setType(byte[].class.getName())
                    .setPayload(ByteString.copyFrom("Hallo Welt".getBytes()))
                    .build();

            try (final IntermediateEnvelope<Application> envelope = new IntermediateEnvelope<>(null, publicHeader, privateHeader, body)) {
                envelope.incrementHopCount();

                assertEquals(1, envelope.getHopCount());
                assertEquals(1, IntermediateEnvelope.of(envelope.getOrBuildByteBuf()).getHopCount());
            }
        }
    }

    @Nested
    class GetSignature {
        @Test
        void shouldReturnSignature() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

            assertArrayEquals(new byte[]{}, envelope.getSignature());
        }

        @Test
        void shouldThrowExceptionOnError() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
            when(envelope.getPublicHeader()).thenThrow(IOException.class);

            assertThrows(IOException.class, envelope::getSignature);
        }
    }

    @Nested
    class Arm {
        @Test
        void shouldReturnSignedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);
            try (final IntermediateEnvelope<MessageLite> armedEnvelop = envelope.armAndRelease(senderPrivateKey)) {
                assertNotNull(armedEnvelop.getPublicHeader().getSignature());
            }
        }

        @Test
        void getPrivatHeaderShouldFailOnArmedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);
            try (final IntermediateEnvelope<MessageLite> armedEnvelop = envelope.armAndRelease(senderPrivateKey)) {
                assertThrows(IOException.class, armedEnvelop::getPrivateHeader);
            }
        }

        @Test
        void getBodyShouldFailOnArmedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);
            try (final IntermediateEnvelope<MessageLite> armedEnvelop = envelope.armAndRelease(senderPrivateKey)) {
                assertThrows(IOException.class, armedEnvelop::getBody);
            }
        }
    }

    @Nested
    class Disarm {
        private IntermediateEnvelope<MessageLite> envelope;
        private IntermediateEnvelope<MessageLite> armedEnvelop;

        @BeforeEach
        void setUp() throws IOException {
            envelope = IntermediateEnvelope.of(message);
            armedEnvelop = envelope.armAndRelease(senderPrivateKey);
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(armedEnvelop);
        }

        @Test
        void shouldReturnDisarmedMessageIfSignatureIsValid() throws IOException {
            final IntermediateEnvelope<MessageLite> disarmedMessage = armedEnvelop.disarmAndRelease(senderPrivateKey);

            assertNotNull(disarmedMessage);
        }

        @Test
        void shouldThrowExceptionIfSignatureIsNotValid() throws IOException {
            final IntermediateEnvelope<MessageLite> rearmed = envelope.armAndRelease(recipientPrivateKey);

            // arm with wrong private key
            assertThrows(IOException.class, () -> rearmed.disarmAndRelease(recipientPrivateKey));
        }

        @Test
        void getPrivatHeaderShouldNotFailOnDisarmedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> disarmedEnvelope = armedEnvelop.disarmAndRelease(recipientPrivateKey);

            assertNotNull(disarmedEnvelope.getPrivateHeader());
        }

        @Test
        void getBodyShouldNotFailOnDisarmedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> disarmedEnvelope = armedEnvelop.disarmAndRelease(recipientPrivateKey);

            assertNotNull(disarmedEnvelope.getBodyAndRelease());
        }
    }

    @Nested
    class TestAcknowledgement {
        @Test
        void shouldCreateEnvelopeWithAcknowledgementMessage() throws IOException {
            final IntermediateEnvelope<Acknowledgement> acknowledgement = IntermediateEnvelope.acknowledgement(1, senderPublicKey, senderProofOfWork, recipientPublicKey, messageId);

            assertEquals(1, acknowledgement.getPublicHeader().getNetworkId());
            assertEquals(ACKNOWLEDGEMENT, acknowledgement.getPrivateHeader().getType());
            assertEquals(messageId.longValue(), acknowledgement.getBodyAndRelease().getCorrespondingId());
        }
    }

    @Nested
    class TestApplication {
        @Test
        void shouldCreateEnvelopeWithApplicationMessage() throws IOException {
            final IntermediateEnvelope<Application> application = IntermediateEnvelope.application(1, senderPublicKey, senderProofOfWork, recipientPublicKey, String.class.getName(), new byte[]{});

            assertEquals(1, application.getPublicHeader().getNetworkId());
            assertEquals(APPLICATION, application.getPrivateHeader().getType());
            assertEquals(String.class.getName(), application.getBodyAndRelease().getType());
        }
    }

    @Nested
    class TestDiscovery {
        @Test
        void shouldCreateEnvelopeWithDiscoveryMessage() throws IOException {
            final IntermediateEnvelope<Discovery> discovery = IntermediateEnvelope.discovery(1, senderPublicKey, senderProofOfWork, recipientPublicKey, 1337L);

            assertEquals(1, discovery.getPublicHeader().getNetworkId());
            assertEquals(DISCOVERY, discovery.getPrivateHeader().getType());
            assertEquals(1337L, discovery.getBodyAndRelease().getChildrenTime());
        }
    }

    @Nested
    class TestUnite {
        @Test
        void shouldCreateEnvelopeWithDiscoveryMessage() throws IOException {
            final IntermediateEnvelope<Unite> unite = IntermediateEnvelope.unite(1, senderPublicKey, senderProofOfWork, recipientPublicKey, senderPublicKey, new InetSocketAddress(22527));

            assertEquals(1, unite.getPublicHeader().getNetworkId());
            assertEquals(UNITE, unite.getPrivateHeader().getType());
            assertEquals(ByteString.copyFrom(senderPublicKey.byteArrayValue()), unite.getBodyAndRelease().getPublicKey());
        }
    }

    @Nested
    class TestMagicNumber {
        @Test
        void shouldBeTheCorrectMagicNumber() {
            final int magicNumber = (int) Math.pow(22527, 2);
            final byte[] expectedMagicNumber = ByteBuffer.allocate(4).putInt(magicNumber).array();

            assertArrayEquals(expectedMagicNumber, IntermediateEnvelope.magicNumber());
            assertEquals(magicNumber, ByteBuffer.wrap(IntermediateEnvelope.magicNumber()).getInt());
        }
    }
}
