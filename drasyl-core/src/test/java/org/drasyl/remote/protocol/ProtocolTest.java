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

import com.google.protobuf.ByteString;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.UnsignedShort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

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

    @Nested
    class TestUnite {
        @Test
        void shouldSerializeIpv4ToCorrectSize() {
            final InetSocketAddress address = new InetSocketAddress("37.61.174.58", 80);
            final Unite unite = Unite.newBuilder()
                    .setPublicKey(ByteString.copyFrom(CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").byteArrayValue()))
                    .setAddress(address.getHostString())
                    .setPort(ByteString.copyFrom(UnsignedShort.of(address.getPort()).toBytes()))
                    .build();

            assertEquals(53, unite.getSerializedSize());
        }

        @Test
        void shouldSerializeIpv6ToCorrectSize() {
            final InetSocketAddress address = new InetSocketAddress("b719:5781:d127:d17c:1230:b24c:478c:7985", 443);
            final Unite unite = Unite.newBuilder()
                    .setPublicKey(ByteString.copyFrom(CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb").byteArrayValue()))
                    .setAddress(address.getHostString())
                    .setPort(ByteString.copyFrom(UnsignedShort.of(address.getPort()).toBytes()))
                    .build();

            assertEquals(80, unite.getSerializedSize());
        }
    }
}
