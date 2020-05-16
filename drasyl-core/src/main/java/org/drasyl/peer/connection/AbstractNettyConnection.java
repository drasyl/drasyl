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
package org.drasyl.peer.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.ResponseMessage;
import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.drasyl.util.Pair;
import org.slf4j.Logger;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link AbstractNettyConnection} object models an in- or outbound connection by netty.
 */
@SuppressWarnings({ "squid:S00107", "java:S2160" })
public abstract class AbstractNettyConnection extends PeerConnection {
    protected final ConcurrentHashMap<String, Pair<Class<? extends ResponseMessage<?, ?>>, SingleEmitter<ResponseMessage<?, ?>>>> emitters;
    protected final Channel channel;
    protected final String userAgent;
    protected final URI endpoint;
    private final String channelId;
    protected AtomicBoolean isClosed;
    protected CompletableFuture<Boolean> closedCompletable;
    protected ChannelFutureListener channelCloseFutureListener;

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel            channel of the connection
     * @param endpoint           the URI of the target system
     * @param identity           the identity of this {@link NodeServerClientConnection}
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public AbstractNettyConnection(Channel channel,
                                   URI endpoint,
                                   Identity identity,
                                   ConnectionsManager connectionsManager) {
        this(channel, endpoint, identity, "U/A", connectionsManager);
    }

    /**
     * Creates a new connection.
     *
     * @param channel            channel of the connection
     * @param endpoint           the URI of the target system
     * @param identity           the identity of this {@link NodeServerClientConnection}
     * @param userAgent          the User-Agent string
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public AbstractNettyConnection(Channel channel,
                                   URI endpoint,
                                   Identity identity,
                                   String userAgent,
                                   ConnectionsManager connectionsManager) {
        this(channel, userAgent, identity, endpoint, new AtomicBoolean(false), new ConcurrentHashMap<>(), new CompletableFuture<>(), connectionsManager);

        channelCloseFutureListener = future -> {
            if (future.isSuccess()) {
                getLogger().debug("[{}]: The channel have been closed successfully.", future.channel().id().asShortText());
                closedCompletable.complete(true);
            }
            else {
                getLogger().error("[{}]: The channel could not be closed: {}", future.channel().id().asShortText(), future.cause());
            }
        };

        this.channel.closeFuture().addListener(channelCloseFutureListener);
    }

    protected AbstractNettyConnection(Channel channel,
                                      String userAgent,
                                      Identity identity,
                                      URI endpoint,
                                      AtomicBoolean isClosed,
                                      ConcurrentHashMap<String, Pair<Class<? extends ResponseMessage<?, ?>>, SingleEmitter<ResponseMessage<?, ?>>>> emitters,
                                      CompletableFuture<Boolean> closedCompletable,
                                      ConnectionsManager connectionsManager) {
        super(identity);
        this.emitters = emitters;
        this.channel = channel;
        this.userAgent = userAgent;
        this.endpoint = endpoint;
        this.isClosed = isClosed;
        this.closedCompletable = closedCompletable;
        this.channelId = channel.id().asShortText();

        connectionsManager.addConnection(this, this::close);
    }

    /**
     * Returns the correct logger. Is needed for sub-classes.
     */
    protected abstract Logger getLogger();

    /**
     * Returns the channel close future.
     */
    public ChannelFuture getCloseFuture() {
        return channel.closeFuture();
    }

    @Override
    protected void close(CloseReason reason) {
        if (isClosed.compareAndSet(false, true)) {
            emitters.clear();
            channel.writeAndFlush(new QuitMessage(reason)).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void send(Message<?> message) {
        if (message != null && !isClosed.get() && channel.isOpen()) {
            channel.writeAndFlush(message);
        }
        else {
            getLogger().info("[{} Can't send message {}", AbstractNettyConnection.this, message);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>>> Single<T> send(
            RequestMessage<?> message,
            Class<T> responseClass) {
        return (Single<T>) Single.<ResponseMessage<?, ?>>create(emitter -> {
            if (isClosed.get()) {
                emitter.onError(new IllegalStateException("This connection is already prompt to close."));
            }
            else if (emitters.putIfAbsent(message.getId(), Pair.of(responseClass, emitter)) == null) {
                send(message);
            }
        });
    }

    @Override
    public void setResponse(ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>> response) {
        Pair<Class<? extends ResponseMessage<?, ?>>, SingleEmitter<ResponseMessage<?, ?>>> pair = emitters.remove(response.getCorrespondingId());
        if (pair != null && pair.first().isInstance(response)) {
            pair.second().onSuccess(response);
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
    public CompletableFuture<Boolean> isClosed() {
        return closedCompletable;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AbstractNettyConnection that = (AbstractNettyConnection) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public String toString() {
        return MessageFormat.format(getClass().getSimpleName() + " [{0}/Channel:{1}]", identity, channelId);
    }
}
