package org.drasyl.remote.protocol;

import com.google.protobuf.ByteString;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.UnsignedShort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtocolTest {
    @Nested
    class TestPublicHeader {
        @Test
        void shouldSerializeToCorrectSize() {
            final PublicHeader header = PublicHeader.newBuilder()
                    .setId(MessageId.of("9a64cb2f5e214d2b").longValue())
                    .setNetworkId(Integer.MIN_VALUE)
                    .setSender(ByteString.copyFrom(CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").byteArrayValue()))
                    .setProofOfWork(ProofOfWork.of(Integer.MIN_VALUE).intValue())
                    .setRecipient(ByteString.copyFrom(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458").byteArrayValue()))
                    .setHopCount(1)
                    .build();

            assertEquals(93, header.getSerializedSize());
        }

        @Test
        void shouldSerializeHeadChunkToCorrectSize() {
            final PublicHeader header = PublicHeader.newBuilder()
                    .setId(MessageId.of("9a64cb2f5e214d2b").longValue())
                    .setNetworkId(Integer.MIN_VALUE)
                    .setSender(ByteString.copyFrom(CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").byteArrayValue()))
                    .setProofOfWork(ProofOfWork.of(Integer.MIN_VALUE).intValue())
                    .setRecipient(ByteString.copyFrom(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458").byteArrayValue()))
                    .setHopCount(1)
                    .setTotalChunks(UnsignedShort.of(123).getValue())
                    .build();

            assertEquals(95, header.getSerializedSize());
        }

        @Test
        void shouldSerializeNonHeadChunkToCorrectSize() {
            final PublicHeader header = PublicHeader.newBuilder()
                    .setId(MessageId.of("9a64cb2f5e214d2b").longValue())
                    .setNetworkId(Integer.MIN_VALUE)
                    .setSender(ByteString.copyFrom(CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").byteArrayValue()))
                    .setProofOfWork(ProofOfWork.of(Integer.MIN_VALUE).intValue())
                    .setRecipient(ByteString.copyFrom(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458").byteArrayValue()))
                    .setHopCount(1)
                    .setChunkNo(UnsignedShort.of(64).getValue())
                    .build();

            assertEquals(95, header.getSerializedSize());
        }
    }
}
