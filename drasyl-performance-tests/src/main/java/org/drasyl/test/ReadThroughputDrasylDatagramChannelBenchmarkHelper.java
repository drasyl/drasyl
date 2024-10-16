/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;

/**
 * Receives UDP packets for 60 seconds and calculates the read throughput.
 * Results are used to compare different channels.
 */
public class ReadThroughputDrasylDatagramChannelBenchmarkHelper {
    private static final String IDENTITY = SystemPropertyUtil.get("identity", "benchmark.identity");
    private static final String SENDER = SystemPropertyUtil.get("sender", "benchmark_sender.identity");
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 10);

    public static void main(final String[] args) throws InterruptedException, IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        // load/create sender
        final File senderFile = new File(SENDER);
        if (!senderFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(senderFile.toPath(), Identity.generateIdentity());
        }
        final Identity sender = IdentityManager.readIdentityFile(senderFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final ByteBuf payload = Unpooled.wrappedBuffer(new byte[PACKET_SIZE]);
        final ApplicationMessage message = ApplicationMessage.of(1, identity.getIdentityPublicKey(), sender.getIdentityPublicKey(), sender.getProofOfWork(), payload);

        System.out.println(message);

        final UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        final ByteBuf encoded = message.encodeMessage(alloc);

        // Convert ByteBuf to the desired \x formatted string
        final StringBuilder hexString = new StringBuilder();
        while (encoded.isReadable()) {
            hexString.append(String.format("\\x%02x", encoded.readUnsignedByte()));
        }

        System.out.println("yes \"$(echo -en '" + hexString + "')\" | ncat --send-only --udp 127.0.0.1 12345");
    }
}
