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
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * The {@link AbstractNettyConnection} object models an in- or outbound connection by netty.
 */
@SuppressWarnings({ "squid:S00107", "java:S2160" })
public abstract class AbstractNettyConnection extends PeerConnection {
    protected final Channel channel;
    protected final String userAgent;
    private final String channelId;
    protected AtomicBoolean isClosed;
    protected CompletableFuture<Boolean> closedCompletable;

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel            channel of the connection
     * @param identity           the identity of this {@link NodeServerConnection}
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public AbstractNettyConnection(Channel channel,
                                   Identity identity,
                                   ConnectionsManager connectionsManager) {
        this(channel, identity, "U/A", connectionsManager);
    }

    /**
     * Creates a new connection.
     *
     * @param channel            channel of the connection
     * @param identity           the identity of this {@link NodeServerConnection}
     * @param userAgent          the User-Agent string
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public AbstractNettyConnection(Channel channel,
                                   Identity identity,
                                   String userAgent,
                                   ConnectionsManager connectionsManager) {
        this(channel, userAgent, identity, new AtomicBoolean(false), new CompletableFuture<>(), connectionsManager);

        this.channel.closeFuture().addListener((ChannelFutureListener) this::onChannelClose);
    }

    protected AbstractNettyConnection(Channel channel,
                                      String userAgent,
                                      Identity identity,
                                      AtomicBoolean isClosed,
                                      CompletableFuture<Boolean> closedCompletable,
                                      ConnectionsManager connectionsManager) {
        super(identity);
        this.channel = channel;
        this.userAgent = userAgent;
        this.isClosed = isClosed;
        this.closedCompletable = closedCompletable;
        this.channelId = channel.id().asShortText();

        connectionsManager.addConnection(this, this::close);
    }

    /**
     * This method is called when the netty channel closes.
     *
     * @param future
     */
    protected void onChannelClose(ChannelFuture future) {
        if (future.isSuccess()) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("[{}]: The channel have been closed successfully.", future.channel().id().asShortText());
            }
            closedCompletable.complete(true);
        }
        else {
            if (getLogger().isDebugEnabled()) {
                getLogger().error("[{}]: The channel could not be closed: ", future.channel().id().asShortText(), future.cause());
            }
        }
    }

    @Override
    protected void close(CloseReason reason) {
        if (isClosed.compareAndSet(false, true)) {
            channel.writeAndFlush(new QuitMessage(reason)).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Returns the correct logger. Is needed for sub-classes.
     */
    protected abstract Logger getLogger();

    @Override
    public void send(Message message) {
        requireNonNull(message);
        if (!isClosed.get() && channel.isOpen()) {
            channel.writeAndFlush(message);
        }
        else {
            getLogger().info("[{} Can't send message {}", AbstractNettyConnection.this, message);
        }
    }

    @Override
    public String getUserAgent() {
        return this.userAgent;
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

    /**
     * Returns the channel close future.
     */
    public ChannelFuture getCloseFuture() {
        return channel.closeFuture();
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0} [{1}/Channel:{2}]", getClass().getSimpleName(), identity.getAddress(), channelId);
    }
}
