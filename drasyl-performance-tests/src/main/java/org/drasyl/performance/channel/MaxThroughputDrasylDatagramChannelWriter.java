/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.performance.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.DefaultDrasylServerChannelInitializer;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.netty.channel.ChannelOption.IP_TOS;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;

/**
 * Writes for 60 seconds as fast as possible to the {@link DatagramChannel} used by drasyl and
 * prints the write throughput. Results are used as a baseline to compare with other channels.
 *
 * @see MaxThroughputDatagramChannelWriter
 */
@SuppressWarnings({ "java:S106", "java:S3776", "java:S4507" })
public class MaxThroughputDrasylDatagramChannelWriter extends AbstractMaxThroughputWriter {
    private static final String CLAZZ_NAME = StringUtil.simpleClassName(MaxThroughputDrasylDatagramChannelWriter.class);
    private static final String HOST = SystemPropertyUtil.get("host", "127.0.0.1");
    private static final int PORT = SystemPropertyUtil.getInt("port", 12345);
    private static final String IDENTITY = SystemPropertyUtil.get("identity", "benchmark.identity");
    private static final int PACKET_SIZE = SystemPropertyUtil.getInt("packetsize", 1024);
    private static final int DURATION = SystemPropertyUtil.getInt("duration", 10);
    private static final String RECIPIENT = SystemPropertyUtil.get("recipient", "c909a27d9ec0127c57142c3e1547ba9f82bc605277380b2a8fc0fabafe2be4c9");
    private static final int DRASYL_OVERHEAD = 104;
    private static final List<Long> throughputPerSecond = new ArrayList<>();
    private Identity identity;
    private EventLoopGroup group;
    private EventLoopGroup udpGroup;

    private MaxThroughputDrasylDatagramChannelWriter() {
        super(DURATION);
    }

    public static void main(final String[] args) throws Exception {
        System.out.printf("%s : HOST: %s%n", CLAZZ_NAME, HOST);
        System.out.printf("%s : PORT: %d%n", CLAZZ_NAME, PORT);
        System.out.printf("%s : IDENTITY: %s%n", CLAZZ_NAME, IDENTITY);
        System.out.printf("%s : PACKET_SIZE: %d%n", CLAZZ_NAME, PACKET_SIZE);
        System.out.printf("%s : DURATION: %d%n", CLAZZ_NAME, DURATION);
        System.out.printf("%s : RECIPIENT: %s%n", CLAZZ_NAME, RECIPIENT);

        new MaxThroughputDrasylDatagramChannelWriter().run();
    }

    @Override
    protected long bytesWritten(WriteHandler<?> writeHandler) {
        return (writeHandler.messagesWritten() + DRASYL_OVERHEAD) * PACKET_SIZE;
    }

    @Override
    protected Channel setupChannel() throws InterruptedException, IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        identity = IdentityManager.readIdentityFile(identityFile.toPath());

        group = new NioEventLoopGroup();
        udpGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        final Channel channel = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                        .option(IP_TOS, 0xB8)
                        .group(udpGroup.next())
                        .channel(EventLoopGroupUtil.getBestDatagramChannel())
                        .handler(new UdpServerChannelInitializer(parent)))
                .option(ARMING_ENABLED, false)
                .handler(new DefaultDrasylServerChannelInitializer())
                .childHandler(new ChannelInboundHandlerAdapter())
                .bind(identity)
                .sync()
                .channel();

        DatagramChannel udpChannel;
        while (true) {
            final UdpServer udpServer = channel.pipeline().get(UdpServer.class);
            if (udpServer != null) {
                udpChannel = udpServer.udpChannel();
                if (udpChannel != null) {
                    return udpChannel;
                }
            }

            System.out.println("Wait for udpChannel...");
            Thread.sleep(1000);
        }
    }

    @Override
    protected Object buildMsg() {
        final InetSocketAddress targetAddress = new InetSocketAddress(HOST, PORT);
        final IdentityPublicKey recipient = IdentityPublicKey.of(RECIPIENT);
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[PACKET_SIZE]);
        final ApplicationMessage appMsg = ApplicationMessage.of(1, recipient, identity.getIdentityPublicKey(), identity.getProofOfWork(), data);
        return new InetAddressedMessage<>(appMsg, targetAddress);
    }

    @Override
    protected Function<Object, Object> getMsgDuplicator() {
        return new Function<Object, Object>() {
            @Override
            public Object apply(final Object o) {
                final InetAddressedMessage<ApplicationMessage> oldInetMsg = (InetAddressedMessage<ApplicationMessage>) o;
                final ApplicationMessage oldAppMsg = oldInetMsg.content();
                final ApplicationMessage appMsg = ApplicationMessage.of(1, (IdentityPublicKey) oldAppMsg.getRecipient(), (IdentityPublicKey) oldAppMsg.getSender(), oldAppMsg.getProofOfWork(), oldAppMsg.getPayload().retainedDuplicate());
                return oldInetMsg.replace(appMsg);
            }
        };
    }

    @Override
    protected void teardown() throws InterruptedException {
        group.shutdownGracefully().await();
        udpGroup.shutdownGracefully().await();
    }

    @Override
    protected String className() {
        return CLAZZ_NAME;
    }
}
