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
package org.drasyl.handler.remote;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.Identity;
import org.drasyl.node.event.Node;
import org.drasyl.node.event.NodeDownEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;

@State(Scope.Benchmark)
public class UdpServerBenchmark extends AbstractBenchmark {
    private DatagramSocket socket;
    private static CompletableFuture<Void>[] futures;
    private static final AtomicInteger THREAD_INDEX = new AtomicInteger(0);
    private InetAddress localHost;
    private int port;
    private UserEventAwareEmbeddedChannel channel;
    private Identity identity2;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        try {
            futures = new CompletableFuture[Runtime.getRuntime().availableProcessors()];
            socket = new DatagramSocket();

            identity2 = IdentityTestUtil.ID_2;

            final UdpServer handler = new UdpServer(InetAddress.getLocalHost(), 0);

            channel = new UserEventAwareEmbeddedChannel(
                    handler,
                    new SimpleChannelInboundHandler<InetAddressedMessage<?>>() {
                        @Override
                        protected void channelRead0(final ChannelHandlerContext ctx,
                                                    final InetAddressedMessage<?> msg) {
                            if (msg.content() instanceof ByteBuf) {
                                final int index = ((ByteBuf) msg.content()).readableBytes();
                                futures[index].complete(null);
                            }
                            else {
                                ctx.fireChannelRead(msg);
                            }
                        }
                    });

            await().until(() -> {
                final Object evt = channel.readEvent();
                if (evt instanceof UdpServer.Port) {
                    port = ((UdpServer.Port) evt).getPort();
                    return true;
                }
                else {
                    return false;
                }
            });
            localHost = InetAddress.getLocalHost();
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    @TearDown
    public void teardown() {
        channel.pipeline().fireUserEventTriggered(NodeDownEvent.of(Node.of(identity2)));
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private int index;

        public ThreadState() {
            try {
                index = THREAD_INDEX.getAndIncrement();
            }
            catch (final Exception e) {
                handleUnexpectedException(e);
            }
        }
    }

    @Benchmark
    @Threads(Threads.MAX)
    @BenchmarkMode(Mode.Throughput)
    public void receiveMaxThreads(final ThreadState state) {
        try {
            futures[state.index] = new CompletableFuture<>();
            socket.send(new DatagramPacket(new byte[state.index], state.index, localHost, port));
            futures[state.index].join();
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void receiveSingleThread(final ThreadState state) {
        try {
            futures[state.index] = new CompletableFuture<>();
            socket.send(new DatagramPacket(new byte[state.index], state.index, localHost, port));
            futures[state.index].join();
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }
}
