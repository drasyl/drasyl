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
package org.drasyl.remote.handler;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.drasyl.AbstractBenchmark;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.Endpoint;
import org.drasyl.remote.handler.InternetDiscovery.Peer;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.util.Pair;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@State(Scope.Benchmark)
public class InternetDiscoveryBenchmark extends AbstractBenchmark {
    private Map<Nonce, InternetDiscovery.Ping> openPingsCache;
    private Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;
    private Map<IdentityPublicKey, Peer> peers;
    private Set<IdentityPublicKey> directConnectionPeers;
    private InternetDiscovery handler;
    private ChannelHandlerContext ctx;
    private SocketAddress recipient;
    private ApplicationMessage msg;
    private ChannelPromise future;
    private Set<IdentityPublicKey> superPeers;
    private IdentityPublicKey bestSuperPeer;
    private Duration pingInterval;
    private Duration pingTimeout;
    private Duration pingCommunicationTimeout;
    private boolean superPeerEnabled;
    private Set<Endpoint> superPeerEndpoints;
    private int networkId;

    @Setup
    public void setup() {
        openPingsCache = new HashMap<>();
        uniteAttemptsCache = new HashMap<>();
        peers = new HashMap<>();
        directConnectionPeers = new HashSet<>();
        superPeers = new HashSet<>();
        pingInterval = Duration.ofSeconds(5);
        pingTimeout = Duration.ofSeconds(30);
        pingCommunicationTimeout = Duration.ofSeconds(60);
        superPeerEndpoints = Set.of();
        final Identity identity = IdentityTestUtil.ID_1;
        handler = new InternetDiscovery(openPingsCache, identity.getAddress(), identity.getProofOfWork(), uniteAttemptsCache, peers, directConnectionPeers, superPeers, pingInterval, pingTimeout, pingCommunicationTimeout, superPeerEnabled, superPeerEndpoints, networkId, null, bestSuperPeer);

        ctx = new MyHandlerContext();
        recipient = new MyAddress();
        final IdentityPublicKey recipient = IdentityTestUtil.ID_1.getIdentityPublicKey();
        msg = ApplicationMessage.of(1, IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_2.getProofOfWork(), recipient, byte[].class.getName(), ByteString.EMPTY);

        future = new MyChannelPromise();

        directConnectionPeers.add(recipient);
        final Peer peer = new Peer();
        peer.inboundPingOccurred();
        peer.setAddress(new InetSocketAddress("127.0.0.1", 25527));
        peers.put(recipient, peer);
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void matchedWrite() {
        try {
            handler.write(ctx, new AddressedMessage<>(msg, recipient), future);
        }
        catch (final Exception e) {
            handleUnexpectedException(e);
        }
    }

    private static class MyHandlerContext implements ChannelHandlerContext {
        @Override
        public Channel channel() {
            return null;
        }

        @Override
        public EventExecutor executor() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public ChannelHandler handler() {
            return null;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(final Object evt) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(final Object msg) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            return null;
        }

        @Override
        public ChannelFuture bind(final SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress,
                                     final SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture disconnect() {
            return null;
        }

        @Override
        public ChannelFuture close() {
            return null;
        }

        @Override
        public ChannelFuture deregister() {
            return null;
        }

        @Override
        public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress,
                                     final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(final SocketAddress remoteAddress,
                                     final SocketAddress localAddress,
                                     final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture disconnect(final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture close(final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture deregister(final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelHandlerContext read() {
            return null;
        }

        @Override
        public ChannelFuture write(final Object msg) {
            return null;
        }

        @Override
        public ChannelFuture write(final Object msg, final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelHandlerContext flush() {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(final Object msg, final ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(final Object msg) {
            return null;
        }

        @Override
        public ChannelPromise newPromise() {
            return null;
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return null;
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return null;
        }

        @Override
        public ChannelFuture newFailedFuture(final Throwable cause) {
            return null;
        }

        @Override
        public ChannelPromise voidPromise() {
            return null;
        }

        @Override
        public ChannelPipeline pipeline() {
            return null;
        }

        @Override
        public ByteBufAllocator alloc() {
            return null;
        }

        @Override
        public <T> Attribute<T> attr(final AttributeKey<T> key) {
            return null;
        }

        @Override
        public <T> boolean hasAttr(final AttributeKey<T> key) {
            return false;
        }
    }

    private static class MyAddress extends SocketAddress {
    }

    private static class MyChannelPromise implements ChannelPromise {
        @Override
        public Channel channel() {
            return null;
        }

        @Override
        public ChannelPromise setSuccess(final Void result) {
            return null;
        }

        @Override
        public boolean trySuccess(final Void result) {
            return false;
        }

        @Override
        public ChannelPromise setSuccess() {
            return null;
        }

        @Override
        public boolean trySuccess() {
            return false;
        }

        @Override
        public ChannelPromise setFailure(final Throwable cause) {
            return null;
        }

        @Override
        public boolean tryFailure(final Throwable cause) {
            return false;
        }

        @Override
        public boolean setUncancellable() {
            return false;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isCancellable() {
            return false;
        }

        @Override
        public Throwable cause() {
            return null;
        }

        @Override
        public ChannelPromise addListener(final GenericFutureListener<? extends Future<? super Void>> listener) {
            return null;
        }

        @Override
        public ChannelPromise addListeners(final GenericFutureListener<? extends Future<? super Void>>... listeners) {
            return null;
        }

        @Override
        public ChannelPromise removeListener(final GenericFutureListener<? extends Future<? super Void>> listener) {
            return null;
        }

        @Override
        public ChannelPromise removeListeners(final GenericFutureListener<? extends Future<? super Void>>... listeners) {
            return null;
        }

        @Override
        public ChannelPromise sync() {
            return null;
        }

        @Override
        public ChannelPromise syncUninterruptibly() {
            return null;
        }

        @Override
        public ChannelPromise await() {
            return null;
        }

        @Override
        public ChannelPromise awaitUninterruptibly() {
            return null;
        }

        @Override
        public boolean await(final long timeout, final TimeUnit unit) {
            return false;
        }

        @Override
        public boolean await(final long timeoutMillis) {
            return false;
        }

        @Override
        public boolean awaitUninterruptibly(final long timeout, final TimeUnit unit) {
            return false;
        }

        @Override
        public boolean awaitUninterruptibly(final long timeoutMillis) {
            return false;
        }

        @Override
        public Void getNow() {
            return null;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Void get(final long timeout,
                        final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }

        @Override
        public boolean isVoid() {
            return false;
        }

        @Override
        public ChannelPromise unvoid() {
            return null;
        }
    }
}
