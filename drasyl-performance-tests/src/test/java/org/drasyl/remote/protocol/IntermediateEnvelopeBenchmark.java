/*
 * Copyright (c) 2021.
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
import io.netty.buffer.Unpooled;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.Random;

import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class IntermediateEnvelopeBenchmark {
    private ByteBuf byteBuf;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private Application body;
    private CompressedPrivateKey privateKey;
    private ByteBuf armedByteBuf;

    public IntermediateEnvelopeBenchmark() {
        try {
            byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("600a0c97b4a09744b6f5fef8c27af3120200012221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22288eee8d033221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f223a010002080187080a8008d2e59084e89e5ae0f31dbb95d0cdef3d0b20e97a159775461391595e91a17b82867e5c94afd49880356d82e17dbd143e8ab789d026b370fd7cc154597f979086c4e5b8848a44d36f14bf4d971aa69a5f5b61daa408b7ba50628aca608a2bdfb3419671cab40e423b7c6b39e1290f7568cc6b26b288370ddbdb1149c01469c1c784114d21acb391544fdf1b740ea495473e455d49f76945ba326e700da459f3ae69664ffdb5580d0ee0c60f0d904cefd8a7ea6625aa309e4366f8e4916225df659f075766571c1dc3780829155cd5932011a627927b376f9ba86e49e19ec31c5d2af20d31dc7a8a3dd58eded78228ae2f191dcdcf46200396e1e66a06493b07bfe123692dd3c23c44a6e3f2fff6155bbc70af3e546dbf56a72991082e7a7389edf16130f091995f64109ae5fd10aca2d35d25cb2d5568d3cf514ae158ea938491584f4852ab8f25722c8937920d2014f33aae6d53e74956645afef06978f5719f6bd3b89d7ec598f286d60bf58b46c645f32e442010db1648fcda07d9a7a222c750d0a9a5a79f2b9ca0d0effb119cbeb68ae0109141573eaf2052256d1b68bede8529d0b38b8bfb5f6d99d32d85dd0009953ee11bef4a42c966ec40a5207564fb571a054bd0d3f7f9d6a07decb430c92bbe453423143054c2a0eb6ae1238c0f146cdc013ce2845eda38ef02bacddcb28f42023a17ac1a786355bd6ef136c39f3d3a9e796e6f614157e43f0e28e692cd57736e0651397514393f3d616990d42bc98dc90b0eebe604799eebff2d7646e3df36a1d19440cd0c90cdc7c4ff0bfffbcf059ac87f0d620b611fa3307ad9957037864c9ff1caafb9c9a21c2d44a576d002950342a25013fe3d93f9e0d1d0c211dfef8be765e4954f7f14118d30ff6455e405e9625f736366fd1cbf9fbafe3a664c13031b4a1fe186adae129872e179e6f071a28684fa27896e4d2b31776cb0ac76294c1c0310e896a9ae79e1cdb40b0498c9d38a2aaaf7b556afd740d905c299f479b608e8d112d6f088aba3a206c81b564f93d5b4118f7c0b3f8a68881b342b02e154ea977e0791fc610d59780af486af1be08993fc447a83eecf5526e0b8791b535c2d35bae6ea2394c69066153de0479a686482fe8eb17220671827ee48cbb755d2f21d2798d83d3153a959221913d30ba1faf2f0959df32e7ae34bc00b68167f8b9ac1f126892579e65a7c4fb7c627012a56ae6bda7372e4a9cb6d36d0f3fc007a1e25442ecd7b639d3b825e80064de486267f50e9450c03e84bad15154ebd1a4c1981d1554f001b81a72a35274af7702403e34120f557c0219b2a85a13ad7a25a92254edeafa57d59b6165e493f8b33af775625822a9b43ba1c2291ad541081e7e878f33dd5139e4f29f8889ff66613bad4cca89bc4c129d575a7aa9a0eeb35ac7878fdcc03a444607a3204be56e412025b42"));
            publicHeader = IntermediateEnvelope.buildPublicHeader(0, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), ProofOfWork.of(6518542), CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"));
            privateHeader = PrivateHeader.newBuilder()
                    .setType(APPLICATION)
                    .build();
            final byte[] payload = new byte[1024];
            new Random().nextBytes(payload);
            body = Application.newBuilder()
                    .setType(byte[].class.getName())
                    .setPayload(ByteString.copyFrom(payload))
                    .build();
            privateKey = CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8");
            armedByteBuf = IntermediateEnvelope.of(byteBuf).arm(privateKey).getOrBuildByteBuf();
        }
        catch (final CryptoException | IOException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void byteBuf2Message() {
        try {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(byteBuf);
            envelope.getBody();
        }
        catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void message2ByteBuf() {
        ByteBuf myByteBuf = null;
        try {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(publicHeader, privateHeader, body);
            myByteBuf = envelope.getOrBuildInternalByteBuf();
        }
        catch (final IOException e) {
            e.printStackTrace();
        }
        finally {
            ReferenceCountUtil.safeRelease(myByteBuf);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void arm() {
        IntermediateEnvelope<MessageLite> myArmedEnvelope = null;
        try {
            myArmedEnvelope = IntermediateEnvelope.of(byteBuf).arm(privateKey);
        }
        finally {
            ReferenceCountUtil.safeRelease(myArmedEnvelope);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void disarm() {
        IntermediateEnvelope<MessageLite> myDisarmedEnvelope = null;
        try {
            myDisarmedEnvelope = IntermediateEnvelope.of(armedByteBuf).disarm(privateKey);
        }
        finally {
            ReferenceCountUtil.safeRelease(myDisarmedEnvelope);
        }
    }
}
