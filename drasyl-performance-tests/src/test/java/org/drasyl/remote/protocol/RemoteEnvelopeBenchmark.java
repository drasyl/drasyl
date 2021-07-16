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
import com.goterl.lazysodium.utils.SessionPair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.AbstractBenchmark;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
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
import test.util.IdentityTestUtil;

import java.io.IOException;

import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;

@State(Scope.Benchmark)
public class RemoteEnvelopeBenchmark extends AbstractBenchmark {
    private ByteBuf byteBuf;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private Application body;
    private SessionPair sessionPair;
    private ByteBuf armedByteBuf;

    @Setup
    public void setup() {
        try {
            byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("1e3f5001660a189d3cdfd8c539584f01df95f2b24e06c875b5f4334120c6d01a2018cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad912720c5b58fc20f2a20622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e300102080187080a8008ea7ec311f86b4790ccde3d908ab9109d406aa36f6d0e101c055ddc84a1ab82fdf7a8416fd16ab22ac27415521ff9fdab4a56fea5dfce700a5e1f5632d03991dca1d75672a4def4fbdd7c8cefc6d7c4f214eb9b7dbe5a8ed70cf0e474b162989f2d9a1bc3c695db8ee658fa3b7444b13ff214b1700a4d7f683811e5f7f4aae74621b948b37fd88e5d888f2a2932bbd76bbdc90626f5e59edb33d449d99b61ea81c99aa3788c3304bad504bfa99e53aa135335785166fd4080ecd91a5b7f166f3fb3b7e20d9d12ef35bfc2708b6b1fb18e14f659f077c501157ce32862cb60f749331ffc6ad82a3a4da0517150dcd87f896669dc6ff75c5bf69b325cbb7ff5c92ae00b103255b553adb2a06ef54de1422e3308bb3be9316bb2277a3e2bee0dc16b158861a29f79d6b2987efbffaa8f7d3966d3caeb0334116aa45ea25d401e0f9571220f9f39a8599ed4a15e27f6ced3b172d039c730d7d282b7868ae5f1db73f157b27ddb178571904e410a68c60cf93aca95919c0fe62b70aa3bb29f50949b386f2ca7cb21e22a94d33c3e58c3771e256ae7cefd712437c6ec9959f401d3a2bc2387bfcab4698bde94d596222c8e7a6b4d6320c8dfc1d85a8e5137a77c58f89c23afe10620b9e0f0a3f689a151fcc3cdf43d1e7f119cd4f5c6f6b11e8b6017cee28bb6242b4be87a790d15b519cbb911e60e8d6b78517af370e2c24dd038d47a1614483a8c321ab173721d127dde12432aadfd9db523d14888a224b63d2735613c7272b471a4a04a506145790bc46e79ce1bdced1faa6d8bb1faae4a99a011521139a5f6f952c681b581377964d121456486bfcd42545bfab174025402cff1e71aa8e608bb97e540a9ffaa9ebe92f664804d0d565bbcc308f280e5db6668e8de1d004b743e6e221d1934a23a62893ed03b234137ddc93d960327f58d08d56034a6e751827e3e79df55dda5b52f3acc71b3376c5deea3eed315468b6e02a61b5b83dfdb6f6e77ec2391e7febc158cb0298875bdeeea73e6d07f65fc42bd5bd35c48c7a9c870f9da0565c530759b76d5cc61ce24f7dff762cb3b158f087a7c43b8cc71980cde50c9e74e5836435a00d0e26597638310e5e1af66dece61ccfa8b27aa490af485c027242b6943ac808d83d3c3f51b26731a2da2c4c31c741cec2a40c1053125251cdfdbc0b00c424e7e700f2a5bb1e133a3ca0739529f49248f060bb56d29f0f272ae263670d88f9a1dc87e51120c3a2e897a4e5141f46d74a241486c2e1bb4168aa3a09999d9824edf9f2c70db91c17777ac94b9a34526f10ad40f91becee32d2738af598897d6e7eeddb4c598e5ccd52c176dc3a7a45751ca21e514536b3cc72adedfca4964a4f70db974110b401943bedb31e6e7cbe8a0450a21a7e34ea5e97a0471a3752353a3c2e5de189e73196ea9debe3c4bab2ebddb29cb12025b42"));
            publicHeader = RemoteEnvelope.buildPublicHeader(0, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey());
            privateHeader = PrivateHeader.newBuilder()
                    .setType(APPLICATION)
                    .build();
            final byte[] payload = RandomUtil.randomBytes(1024);
            body = Application.newBuilder()
                    .setType(byte[].class.getName())
                    .setPayload(ByteString.copyFrom(payload))
                    .build();
            sessionPair = Crypto.INSTANCE.generateSessionKeyPair(IdentityTestUtil.ID_1.getKeyAgreementKeyPair(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            armedByteBuf = RemoteEnvelope.of(byteBuf).arm(sessionPair).getOrBuildByteBuf();
        }
        catch (final IOException | CryptoException e) {
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
            myArmedEnvelope = RemoteEnvelope.of(byteBuf).arm(sessionPair);
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
            myDisarmedEnvelope = RemoteEnvelope.of(armedByteBuf).disarm(sessionPair);
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
        finally {
            ReferenceCountUtil.safeRelease(myDisarmedEnvelope);
        }
    }
}
