/*
 * Copyright (c) 2020.
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
package org.drasyl.core.node.connections;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Leave;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.node.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link NettyPeerConnection} object models an in- or outbound connection by netty.
 */
@SuppressWarnings({ "squid:S00107" })
public abstract class NettyPeerConnection implements PeerConnection {
    private static final Logger LOG = LoggerFactory.getLogger(NettyPeerConnection.class);
    protected final ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters;
    protected final NettyPeerConnection self = this; //NOSONAR
    protected final Channel myChannel;
    protected final String userAgent;
    protected final Identity identity;
    protected final URI endpoint;
    protected AtomicBoolean isClosed;
    protected CompletableFuture<Boolean> closedCompletable;
    protected ChannelFutureListener channelCloseFutureListener;

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel  channel of the connection
     * @param endpoint the URI of the target system
     * @param identity the identity of this {@link ClientConnection}
     */
    public NettyPeerConnection(Channel channel, URI endpoint, Identity identity) {
        this(channel, endpoint, identity, "U/A");
    }

    /**
     * Creates a new connection.
     *
     * @param channel   channel of the connection
     * @param endpoint  the URI of the target system
     * @param identity  the identity of this {@link ClientConnection}
     * @param userAgent the User-Agent string
     */
    public NettyPeerConnection(Channel channel, URI endpoint, Identity identity, String userAgent) {
        this(channel, userAgent, identity, endpoint, new AtomicBoolean(false), new ConcurrentHashMap<>(), new CompletableFuture<>());

        channelCloseFutureListener = future -> {
            String msg = "The client and its associated channel";
            if (future.isSuccess()) {
                getLogger().debug("{} {} {} have been closed successfully.", self, msg, future.channel().id());
                closedCompletable.complete(true);
            }
            else {
                getLogger().error("{} {} {} could not be closed: ", self, msg, future.channel().id(), future.cause());
            }

            close();
        };

        myChannel.closeFuture().addListener(channelCloseFutureListener);
    }

    protected NettyPeerConnection(Channel myChannel,
                                  String userAgent,
                                  Identity identity,
                                  URI endpoint,
                                  AtomicBoolean isClosed,
                                  ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters,
                                  CompletableFuture<Boolean> closedCompletable) {
        this.emitters = emitters;
        this.myChannel = myChannel;
        this.userAgent = userAgent;
        this.identity = identity;
        this.endpoint = endpoint;
        this.isClosed = isClosed;
        this.closedCompletable = closedCompletable;
    }

    /**
     * Returns the channel close future.
     */
    public ChannelFuture getCloseFuture() {
        return myChannel.closeFuture();
    }

    @Override
    public void send(IMessage message) {
        if (message != null && !isClosed.get() && myChannel.isOpen()) {
            myChannel.writeAndFlush(message);
        }
        else {
            getLogger().info("[{} Can't send message {}", self, message);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IMessage> Single<T> send(IMessage message,
                                               Class<T> responseClass) {
        return (Single<T>) Single.<IMessage>create(emitter -> {
            if (isClosed.get()) {
                emitter.onError(new IllegalStateException("This connection is already prompt to close."));
            }
            else if (!emitters.containsKey(message.getMessageID())) {
                emitters.put(message.getMessageID(), Pair.of(responseClass, emitter));
                send(message);
            }
        });
    }

    @Override
    public void setResponse(Response<? extends IMessage> response) {
        Pair<Class<? extends IMessage>, SingleEmitter<IMessage>> emitterPair = emitters.get(response.getMsgID());

        if (emitterPair != null && emitterPair.first().isInstance(response.getMessage()) && emitterPair.second() != null) {
            emitterPair.second().onSuccess(response.getMessage());
            emitters.remove(response.getMsgID());
        }
    }

    @Override
    public String getUserAgent() {
        return this.userAgent;
    }

    @Override
    public URI getEndpoint() {
        return this.endpoint;
    }

    @Override
    public Identity getIdentity() {
        return this.identity;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            myChannel.flush();
            emitters.clear();
            myChannel.writeAndFlush(new Leave())
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public CompletableFuture<Boolean> isClosed() {
        return closedCompletable;
    }

    @Override
    public String getConnectionId() {
        return myChannel.id().asLongText();
    }

    @Override
    public String toString() {
        return MessageFormat.format("[Identity:{0}/Channel:{1}]", identity, getConnectionId());
    }

    /**
     * Returns the correct logger. Is needed for sub-classes.
     */
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NettyPeerConnection that = (NettyPeerConnection) o;
        return Objects.equals(identity, that.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity);
    }
}
