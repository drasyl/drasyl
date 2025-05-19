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
package org.drasyl.channel.rs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.AbstractServerChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.JavaDrasylChannel;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_bind;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_bind_free;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_arm_messages;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_build;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_hello_max_age;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_hello_timeout;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_housekeeping_interval;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_id;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_max_peers;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_message_sink;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_min_pow_difficulty;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_network_id;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_new;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_process_unites;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_super_peers;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_udp_port;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_builder_udp_port_none;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_opts_mtu;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_node_send_to;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_recv_buf_free;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_recv_buf_len;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_recv_buf_new;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_recv_buf_recv;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_recv_buf_rx;
import static org.drasyl.channel.rs.Libdrasyl.drasyl_recv_buf_tx;

/**
 * A virtual {@link io.netty.channel.ServerChannel} used for overlay network management. This
 * channel must be bind to an {@link Identity}.
 * <p>
 * (Currently) only compatible with {@link io.netty.channel.nio.NioEventLoop}.
 * <p>
 * Inspired by {@link io.netty.channel.local.LocalServerChannel}.
 *
 * @see JavaDrasylChannel
 */
@UnstableApi
public class RustDrasylServerChannel extends AbstractServerChannel implements DrasylServerChannel {
    private static final Logger LOG = LoggerFactory.getLogger(RustDrasylServerChannel.class);
    public static final byte[] TIMEOUT_SENDER = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
    static Map<DrasylAddress, RustDrasylServerChannel> serverChannels = new ConcurrentHashMap<>();
    private long bind;
    private int mtu;
    private long recvBuf;
    private long recvBufRx;

    enum State {OPEN, ACTIVE, CLOSED}

    private final RustDrasylServerChannelConfig config = new RustDrasylServerChannelConfig(this);
    private final Map<DrasylAddress, RustDrasylChannel> channels;
    private volatile State state;
    private volatile Identity identity; // NOSONAR
    private ChannelPromise activePromise;
    private final EventLoop readLoop = new DefaultEventLoop();
    private boolean readPending;
    final Runnable readTask = this::doRead;
    private final List<Object> readBuf = new ArrayList<>();

    static {
        try {
            new Libdrasyl();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("java:S2384")
    RustDrasylServerChannel(final State state,
                            final Map<DrasylAddress, RustDrasylChannel> channels,
                            final Identity identity,
                            final ChannelPromise activePromise) {
        this.state = requireNonNull(state);
        this.channels = requireNonNull(channels);
        this.identity = identity;
        this.activePromise = activePromise;
    }

    @SuppressWarnings("unused")
    public RustDrasylServerChannel() {
        this(State.OPEN, new ConcurrentHashMap<>(), null, null);
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    @Override
    public Identity identity() {
        return identity;
    }

    @Override
    protected DrasylAddress localAddress0() {
        if (identity != null) {
            return identity.getAddress();
        }
        else {
            return null;
        }
    }

    @Override
    protected void doBind(final SocketAddress identity) {
        if (!(identity instanceof Identity)) {
            throw new IllegalArgumentException("Unsupported address type! Expected `" + Identity.class.getSimpleName() + "`, but got `" + identity.getClass().getSimpleName() + "`.");
        }

        this.identity = (Identity) identity;
        state = State.ACTIVE;

        // build NodeOpts
        final long builder = drasyl_node_opts_builder_new();
        ensureSuccess(drasyl_node_opts_builder_id(builder, this.identity.getIdentitySecretKey().toByteArray(), this.identity.getProofOfWork().getNonce()));
        if (config().getNetworkId() != null) {
            ensureSuccess(drasyl_node_opts_builder_network_id(builder, config().getNetworkId()));
        }

        final long recvBufCap = config().getRecvBufCap();
        recvBuf = drasyl_recv_buf_new(recvBufCap);
        final long recvBufTx = drasyl_recv_buf_tx(recvBuf);
        recvBufRx = drasyl_recv_buf_rx(recvBuf);
        ensureSuccess(drasyl_node_opts_builder_message_sink(builder, recvBufTx));

        if (config().getUdpPort() != null) {
            ensureSuccess(drasyl_node_opts_builder_udp_port(builder, config().getUdpPort()));
        }
        else {
            ensureSuccess(drasyl_node_opts_builder_udp_port_none(builder));
        }
        if (config().isArmMessages() != null) {
            ensureSuccess(drasyl_node_opts_builder_arm_messages(builder, config().isArmMessages()));
        }
        if (config().getMaxPeers() != null) {
            ensureSuccess(drasyl_node_opts_builder_max_peers(builder, config().getMaxPeers()));
        }
        if (config().getMinPowDifficulty() != null) {
            ensureSuccess(drasyl_node_opts_builder_min_pow_difficulty(builder, config().getMinPowDifficulty()));
        }
        if (config().getHelloTimeout() != null) {
            ensureSuccess(drasyl_node_opts_builder_hello_timeout(builder, config().getHelloTimeout().toMillis()));
        }
        if (config().getHelloMaxAge() != null) {
            ensureSuccess(drasyl_node_opts_builder_hello_max_age(builder, config().getHelloMaxAge().toMillis()));
        }
        if (config().getSuperPeers() != null) {
            final StringBuilder superPeers = new StringBuilder();
            for (final Entry<IdentityPublicKey, InetSocketAddress> entry : config().getSuperPeers().entrySet()) {
                if (superPeers.length() > 0) {
                    superPeers.append(" ");
                }
                superPeers.append("udp://").append(entry.getValue().getHostString()).append(":").append(entry.getValue().getPort()).append("?publicKey=").append(entry.getKey()).append("&networkId=").append(config.getNetworkId());

            }
            ensureSuccess(drasyl_node_opts_builder_super_peers(builder, superPeers.toString()));
        }
        if (config().isProcessUnites() != null) {
            ensureSuccess(drasyl_node_opts_builder_process_unites(builder, config().isProcessUnites()));
        }
        if (config().getHousekeepingDelay() != null) {
            ensureSuccess(drasyl_node_opts_builder_housekeeping_interval(builder, config().getHousekeepingDelay().toMillis()));
        }

        final ByteBuffer optsBuf = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        ensureSuccess(drasyl_node_opts_builder_build(builder, optsBuf.array()));
        final long opts = optsBuf.getLong();

        // bind Node
        final ByteBuffer bindBuf = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        mtu = drasyl_node_opts_mtu(opts);
        ensureSuccess(drasyl_node_bind(opts, bindBuf.array()));
        bind = bindBuf.getLong();
    }

    private void ensureSuccess(int resultCode) {
        if (resultCode != 0) {
            throw new RuntimeException("Unexpected result code " + resultCode);
        }
    }

    @Override
    protected void doRegister() throws Exception {
        super.doRegister();

        activePromise = newPromise();

        pipeline().addLast(new ChannelInitializer<>() {
            @Override
            public void initChannel(final Channel ch) {
                ch.pipeline().addLast(new ChannelToLibdrasylHandler(RustDrasylServerChannel.this));
                ch.pipeline().addLast(new DuplicateChannelFilter());
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) {
                        ctx.fireChannelActive();

                        ctx.executor().execute(() -> activePromise.setSuccess());
                        ctx.pipeline().remove(this);
                    }
                });
            }
        });
    }

    @Override
    protected void doClose() {
        if (state != State.CLOSED) {
            // Update the internal state before the closeFuture<?> is notified.
            if (config().isIntraVmDiscoveryEnabled()) {
                serverChannels.remove(identity.getAddress());
            }

            if (identity != null) {
                identity = null;
            }
            state = State.CLOSED;

            drasyl_node_bind_free(bind);
            drasyl_recv_buf_free(recvBuf);
        }
    }

    @Override
    protected void doBeginRead() {
        if (readPending) {
            return;
        }
        if (!isActive()) {
            return;
        }

        readPending = true;
        readLoop.execute(readTask);
    }

    @SuppressWarnings({ "java:S135", "java:S1117", "java:S1181", "java:S1874", "java:S3776" })
    private void doRead() {
        if (!readPending) {
            return;
        }
        readPending = false;

        final ChannelConfig config = config();
        final ChannelPipeline pipeline = pipeline();
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.reset(config);
        final ByteBufAllocator alloc = config().getAllocator();

        Throwable exception = null;
        int recvBufLen = 1;
        try {
            do {
                for (int i = 0; i < recvBufLen; i++) {
                    final byte[] senderBytes = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
                    final byte[] bufBytes = new byte[mtu];
                    final int size = drasyl_recv_buf_recv(bind, recvBufRx, senderBytes, bufBytes, mtu);
                    final IdentityPublicKey sender = IdentityPublicKey.of(senderBytes);
                    final ByteBuf buf = alloc.buffer(size);
                    buf.writeBytes(bufBytes, 0, size);
                    readBuf.add(Pair.of(sender, buf));

                    allocHandle.incMessagesRead(1);
                }
            }
            while (allocHandle.continueReading() && (recvBufLen = drasyl_recv_buf_len(bind)) > 0);
        }
        catch (final Throwable e) {
            exception = e;
        }

        final Set<DrasylAddress> readCompletePending = ConcurrentHashMap.newKeySet();
        final int size = readBuf.size();
        for (int i = 0; i < size; i++) {
            final Object o = readBuf.get(i);
            final Pair<IdentityPublicKey, ByteBuf> pair = (Pair<IdentityPublicKey, ByteBuf>) o;
            final IdentityPublicKey sender = pair.first();
            final ByteBuf buf = pair.second();

            final RustDrasylChannel drasylChannel = this.getChannel(sender);
            if (drasylChannel != null) {
                if (!drasylChannel.isReadBufferFull()) {
                    drasylChannel.queueRead(buf);
                    readCompletePending.add(sender);
                }
                else {
                    ReferenceCountUtil.release(buf);
                }
            }
            else {
                readCompletePending.add(sender);
                this.serve(sender).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        final RustDrasylChannel drasylChannel1 = (RustDrasylChannel) future.channel();
                        drasylChannel1.queueRead(buf);
                    }
                    else {
                        ReferenceCountUtil.release(buf);
                    }

                });
            }
        }
        readBuf.clear();
        allocHandle.readComplete();

        for (final DrasylAddress sender : readCompletePending) {
            final RustDrasylChannel drasylChannel = this.getChannel(sender);
            if (drasylChannel != null && drasylChannel.isRegistered()) {
                drasylChannel.finishRead();
            }
            else {
                readCompletePending.add(sender);
                this.serve(sender).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        final RustDrasylChannel drasylChannel1 = (RustDrasylChannel) future.channel();
                        drasylChannel1.finishRead();
                    }

                });
            }
        }

        boolean closed = false;
        if (exception != null) {
            if (exception instanceof IOException) {
                closed = true;
            }

            if (isOpen()) {
                pipeline.fireExceptionCaught(exception);
            }
        }

        if (closed) {
            if (isOpen()) {
                unsafe().close(unsafe().voidPromise());
            }
        }
        else if (readPending || config.isAutoRead()) {
            read();
        }
    }

    @Override
    public RustDrasylServerChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    protected RustDrasylChannel newDrasylChannel(final DrasylAddress peer) {
        return new RustDrasylChannel(this, peer);
    }

    public Map<DrasylAddress, RustDrasylChannel> getChannels() {
        return channels;
    }

    @Override
    public RustDrasylChannel getChannel(final DrasylAddress peer) {
        return channels.get(peer);
    }

    public ChannelFuture serve(final DrasylAddress peer) {
        final RustDrasylChannel channel;
        if (isOpen()) {
            channel = channels.computeIfAbsent(peer, k -> {
                final RustDrasylChannel ch = newDrasylChannel(k);
                pipeline().fireChannelRead(ch);
                pipeline().fireChannelReadComplete();
                return ch;
            });
        }
        else {
            channel = channels.get(peer);
        }

        if (channel != null) {
            return channel.registeredPromise;
        }
        else {
            return newFailedFuture(new Exception("LibdrasylServerChannel is closed and no LibdrasylChannel exist."));
        }
    }

    private static class ChannelToLibdrasylHandler extends ChannelOutboundHandlerAdapter {
        private final RustDrasylServerChannel parent;

        public ChannelToLibdrasylHandler(final RustDrasylServerChannel parent) {
            this.parent = requireNonNull(parent);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void write(ChannelHandlerContext ctx,
                          Object msg,
                          ChannelPromise promise) throws Exception {
            if (msg instanceof DefaultAddressedEnvelope && ((DefaultAddressedEnvelope<?, ?>) msg).content() instanceof ByteBuf && ((DefaultAddressedEnvelope<?, ?>) msg).recipient() instanceof DrasylAddress) {
                final DrasylAddress recipient = ((DefaultAddressedEnvelope<ByteBuf, DrasylAddress>) msg).recipient();
                final byte[] recipientBytes = recipient.toByteArray();
                final ByteBuf content = ((DefaultAddressedEnvelope<ByteBuf, DrasylAddress>) msg).content();
                final byte[] contentBytes = ByteBufUtil.getBytes(content);
                content.release();

                final int result = drasyl_node_send_to(this.parent.bind, recipientBytes, contentBytes, contentBytes.length);
                if (result == 0) {
                    promise.setSuccess();
                }
                else {
                    promise.setFailure(new Exception("drasyl_node_send_to returned " + result));
                }
            }
            else {
                super.write(ctx, msg, promise);
            }
        }
    }

    /**
     * This handler ensures that we have only one child channel per remote address at a time. If a
     * new child channel is created, the previous one will be closed.
     */
    private static class DuplicateChannelFilter extends SimpleChannelInboundHandler<DrasylChannel> {
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final DrasylChannel msg) {
            msg.closeFuture().addListener(f -> ((RustDrasylServerChannel) ctx.channel()).channels.remove(msg.remoteAddress()));
            ctx.fireChannelRead(msg);
        }
    }
}
