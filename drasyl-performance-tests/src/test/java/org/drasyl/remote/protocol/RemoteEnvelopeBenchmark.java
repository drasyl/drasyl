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
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.AbstractBenchmark;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;

@State(Scope.Benchmark)
public class RemoteEnvelopeBenchmark extends AbstractBenchmark {
    private ByteBuf byteBuf;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private Application body;
    private CompressedPrivateKey privateKey;
    private ByteBuf armedByteBuf;

    @Setup
    public void setup() {
        try {
            byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("600a0c97b4a09744b6f5fef8c27af3120200012221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22288eee8d033221030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f223a010002080187080a8008d2e59084e89e5ae0f31dbb95d0cdef3d0b20e97a159775461391595e91a17b82867e5c94afd49880356d82e17dbd143e8ab789d026b370fd7cc154597f979086c4e5b8848a44d36f14bf4d971aa69a5f5b61daa408b7ba50628aca608a2bdfb3419671cab40e423b7c6b39e1290f7568cc6b26b288370ddbdb1149c01469c1c784114d21acb391544fdf1b740ea495473e455d49f76945ba326e700da459f3ae69664ffdb5580d0ee0c60f0d904cefd8a7ea6625aa309e4366f8e4916225df659f075766571c1dc3780829155cd5932011a627927b376f9ba86e49e19ec31c5d2af20d31dc7a8a3dd58eded78228ae2f191dcdcf46200396e1e66a06493b07bfe123692dd3c23c44a6e3f2fff6155bbc70af3e546dbf56a72991082e7a7389edf16130f091995f64109ae5fd10aca2d35d25cb2d5568d3cf514ae158ea938491584f4852ab8f25722c8937920d2014f33aae6d53e74956645afef06978f5719f6bd3b89d7ec598f286d60bf58b46c645f32e442010db1648fcda07d9a7a222c750d0a9a5a79f2b9ca0d0effb119cbeb68ae0109141573eaf2052256d1b68bede8529d0b38b8bfb5f6d99d32d85dd0009953ee11bef4a42c966ec40a5207564fb571a054bd0d3f7f9d6a07decb430c92bbe453423143054c2a0eb6ae1238c0f146cdc013ce2845eda38ef02bacddcb28f42023a17ac1a786355bd6ef136c39f3d3a9e796e6f614157e43f0e28e692cd57736e0651397514393f3d616990d42bc98dc90b0eebe604799eebff2d7646e3df36a1d19440cd0c90cdc7c4ff0bfffbcf059ac87f0d620b611fa3307ad9957037864c9ff1caafb9c9a21c2d44a576d002950342a25013fe3d93f9e0d1d0c211dfef8be765e4954f7f14118d30ff6455e405e9625f736366fd1cbf9fbafe3a664c13031b4a1fe186adae129872e179e6f071a28684fa27896e4d2b31776cb0ac76294c1c0310e896a9ae79e1cdb40b0498c9d38a2aaaf7b556afd740d905c299f479b608e8d112d6f088aba3a206c81b564f93d5b4118f7c0b3f8a68881b342b02e154ea977e0791fc610d59780af486af1be08993fc447a83eecf5526e0b8791b535c2d35bae6ea2394c69066153de0479a686482fe8eb17220671827ee48cbb755d2f21d2798d83d3153a959221913d30ba1faf2f0959df32e7ae34bc00b68167f8b9ac1f126892579e65a7c4fb7c627012a56ae6bda7372e4a9cb6d36d0f3fc007a1e25442ecd7b639d3b825e80064de486267f50e9450c03e84bad15154ebd1a4c1981d1554f001b81a72a35274af7702403e34120f557c0219b2a85a13ad7a25a92254edeafa57d59b6165e493f8b33af775625822a9b43ba1c2291ad541081e7e878f33dd5139e4f29f8889ff66613bad4cca89bc4c129d575a7aa9a0eeb35ac7878fdcc03a444607a3204be56e412025b42"));
            publicHeader = RemoteEnvelope.buildPublicHeader(0, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), ProofOfWork.of(6518542), CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"));
            privateHeader = PrivateHeader.newBuilder()
                    .setType(APPLICATION)
                    .build();
            final byte[] payload = RandomUtil.randomBytes(1024);
            body = Application.newBuilder()
                    .setType(byte[].class.getName())
                    .setPayload(ByteString.copyFrom(payload))
                    .build();
            privateKey = CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8");
            armedByteBuf = RemoteEnvelope.of(byteBuf).arm(privateKey).getOrBuildByteBuf();
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void byteBuf2Message(final Blackhole blackhole) {
        try {
            final RemoteEnvelope<MessageLite> envelope = RemoteEnvelope.of(byteBuf);
            blackhole.consume(envelope.getBody());
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void message2ByteBuf() {
        ByteBuf myByteBuf = null;
        try {
            final RemoteEnvelope<MessageLite> envelope = RemoteEnvelope.of(publicHeader, privateHeader, body);
            myByteBuf = envelope.getOrBuildInternalByteBuf();
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
        finally {
            ReferenceCountUtil.safeRelease(myByteBuf);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void arm() {
        RemoteEnvelope<MessageLite> myArmedEnvelope = null;
        try {
            myArmedEnvelope = RemoteEnvelope.of(byteBuf).arm(privateKey);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
        finally {
            ReferenceCountUtil.safeRelease(myArmedEnvelope);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void disarm() {
        RemoteEnvelope<MessageLite> myDisarmedEnvelope = null;
        try {
            myDisarmedEnvelope = RemoteEnvelope.of(armedByteBuf).disarm(privateKey);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
        finally {
            ReferenceCountUtil.safeRelease(myDisarmedEnvelope);
        }
    }
}
