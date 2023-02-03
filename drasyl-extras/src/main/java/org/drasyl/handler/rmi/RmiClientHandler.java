/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.rmi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.handler.rmi.message.RmiError;
import org.drasyl.handler.rmi.message.RmiMessage;
import org.drasyl.handler.rmi.message.RmiResponse;
import org.drasyl.util.InternPool;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * A {@link io.netty.channel.ChannelHandler} that can invoke methods on remote objects that are
 * served on other nodes by {@link RmiServerHandler}.
 *
 * @see RmiServerHandler
 */
public class RmiClientHandler extends SimpleChannelInboundHandler<AddressedEnvelope<RmiMessage, SocketAddress>> {
    private static final Logger LOG = LoggerFactory.getLogger(RmiClientHandler.class);
    private final InternPool<Proxy> invocationHandlers = new InternPool<>();
    final Map<UUID, RmiInvocationHandler> requests;
    ChannelHandlerContext ctx;

    RmiClientHandler(final Map<UUID, RmiInvocationHandler> requests) {
        super(false);
        this.requests = requireNonNull(requests);
    }

    /**
     * Creates a new {@link RmiClientHandler}.
     */
    public RmiClientHandler() {
        this(new HashMap<>());
    }

    /*
     * Handler Events
     */

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        this.ctx = null;
    }

    /*
     * Channel Events
     */

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof AddressedEnvelope && (((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof RmiResponse || ((AddressedEnvelope<?, SocketAddress>) msg).content() instanceof RmiError);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedEnvelope<RmiMessage, SocketAddress> msg) {
        LOG.trace("Got `{}`.", msg);
        if (msg.content() instanceof RmiResponse) {
            handleResponse((RmiResponse) msg.content(), msg.sender());
        }
        else if (msg.content() instanceof RmiError) {
            handleError((RmiError) msg.content(), msg.sender());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleResponse(final RmiResponse response, final SocketAddress sender) {
        final UUID id = response.getId();
        final ByteBuf result = response.getResult();

        final RmiInvocationHandler invocationHandler = requests.remove(id);
        if (invocationHandler != null && invocationHandler.getAddress().equals(sender)) {
            invocationHandler.handleResult(id, result);
        }
    }

    private void handleError(final RmiError error, final SocketAddress sender) {
        final UUID id = error.getId();
        final String message = error.getMessage();

        final RmiInvocationHandler invocationHandler = requests.remove(id);
        if (invocationHandler != null && invocationHandler.getAddress().equals(sender)) {
            invocationHandler.handleError(id, message);
        }
    }

    /**
     * Returns a stub class that will pass all method invocation to the binding {@code name} served
     * by {@code address}.
     *
     * @param name    the name of the binding at the remote node
     * @param address the address where the remote object is served
     * @return a reference to a remote object
     * @throws NullPointerException     if {@code name}, {@code clazz}, or {@code address} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code clazz} is not an java interface
     */
    @SuppressWarnings("unchecked")
    public <T> T lookup(final String name, final Class<T> clazz, final SocketAddress address) {
        final T proxy = (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{
                clazz
        }, new RmiInvocationHandler(this, clazz, name, address));
        return (T) invocationHandlers.intern((Proxy) proxy);
    }
}
