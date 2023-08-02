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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;

/**
 * A virtual {@link Channel} for peer communication.
 * <p>
 * (Currently) only compatible with {@link io.netty.channel.nio.NioEventLoop}.
 * <p>
 * Inspired by {@link io.netty.channel.local.LocalChannel}.
 *
 * @see DrasylServerChannel
 */
@UnstableApi
public class DrasylChannel extends AbstractChannel {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylChannel.class);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ')';

    enum State {OPEN, CONNECTED, CLOSED}

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private volatile State state;
    volatile boolean pendingWrites;
    private volatile DrasylAddress localAddress; // NOSONAR
    private final DrasylAddress remoteAddress;

    @UnstableApi
    DrasylChannel(final Channel parent,
                  final State state,
                  final DrasylAddress localAddress,
                  final DrasylAddress remoteAddress) {
        super(parent);
        this.state = state;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    DrasylChannel(final DrasylServerChannel parent, final DrasylAddress remoteAddress) {
        this(parent, null, parent.localAddress0(), remoteAddress);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DrasylChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    @Override
    protected void doRegister() {
        state = State.CONNECTED;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) {
        throw new AlreadyConnectedException();
    }

    @Override
    protected void doDisconnect() {
        doClose();
    }

    @Override
    protected void doClose() {
        localAddress = null;

        state = State.CLOSED;
    }

    @Override
    protected void doBeginRead() {
        // NOOP
        // UdpServer, UdpMulticastServer, TcpServer are currently pushing their readings
    }

    @Override
    protected Object filterOutboundMessage(final Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            return super.filterOutboundMessage(msg);
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @SuppressWarnings("java:S135")
    @Override
    protected void doWrite(final ChannelOutboundBuffer in) throws Exception {
        switch (state) {
            case OPEN:
                throw new NotYetConnectedException();
            case CLOSED:
                throw new ClosedChannelException();
            case CONNECTED:
                break;
        }

        boolean wroteToParent = false;
        pendingWrites = false;
        while (true) {
            final Object msg = in.current();
            if (msg == null) {
                break;
            }

            if (!parent().isWritable()) {
                pendingWrites = true;
                break;
            }

            ReferenceCountUtil.retain(msg);
            final OverlayAddressedMessage<Object> serverChannelMsg = new OverlayAddressedMessage<>(msg, remoteAddress, localAddress);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Pass outbound message `{}` ({}) as `{}` ({}) to server channel.", msg, System.identityHashCode(msg), serverChannelMsg, System.identityHashCode(serverChannelMsg));
            }
            parent().write(serverChannelMsg).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Outbound message `{}` written from channel `{}` to server channel failed:", () -> msg, () -> this, future::cause);
                }
            });
            in.remove();
            wroteToParent = true;
        }

        if (wroteToParent) {
            // only pass flush event to parent channel if we actually have wrote something
            parent().flush();
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.CONNECTED;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    /**
     * Returns {@code true} if remote peer is reachable via a direct path.
     *
     * @return {@code true} if remote peer is reachable via a direct path.
     */
    public boolean isDirectPathPresent() {
        return ((DrasylServerChannel) parent()).isDirectPathPresent(remoteAddress);
    }

    private class DrasylChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress,
                            final SocketAddress localAddress,
                            final ChannelPromise promise) {
            throw new AlreadyConnectedException();
        }
    }
}
