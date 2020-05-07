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
package org.drasyl.core.server.session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Leave;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.node.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link ServerSession} object models the clients of a drasyl node server.
 */
@SuppressWarnings({ "squid:S00107" })
public class ServerSession implements PeerConnection {
    private static final Logger LOG = LoggerFactory.getLogger(ServerSession.class);
    protected final ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters;
    protected final ServerSession self = this; //NOSONAR
    protected final Channel myChannel;
    protected final String userAgent;
    protected final Identity myid;
    private final URI endpoint;
    protected volatile boolean isClosed;
    protected Completable closedCompletable;
    protected CompletableEmitter closedCompletableEmitter;

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel  channel of the connection
     * @param endpoint the URI of the target system
     * @param myid     the identity of this {@link ServerSession}
     */
    public ServerSession(Channel channel, URI endpoint, Identity myid) {
        this(channel, endpoint, myid, "U/A");
    }

    /**
     * Creates a new connection.
     *
     * @param channel   channel of the connection
     * @param endpoint  the URI of the target system
     * @param myid      the identity of this {@link ServerSession}
     * @param userAgent the User-Agent string
     */
    public ServerSession(Channel channel, URI endpoint, Identity myid, String userAgent) {
        this(channel, userAgent, myid, endpoint, false, new ConcurrentHashMap<>(), null, null);

        closedCompletable = Completable.create(emitter -> closedCompletableEmitter = emitter);

        myChannel.closeFuture().addListener((ChannelFutureListener) future -> { //NOSONAR
            String msg = "The client and its associated channel";
            if (future.isSuccess()) {
                getLogger().debug("{} {} {} have been closed successfully.", self, msg, future.channel().id());
                closedCompletableEmitter.onComplete();
            }
            else {
                getLogger().error("{} {} {} could not be closed: ", self, msg, future.channel().id(), future.cause());
            }

            close();
        });
    }

    ServerSession(Channel myChannel,
                  String userAgent,
                  Identity myid,
                  URI endpoint,
                  boolean isClosed,
                  ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters,
                  Completable closedCompletable,
                  CompletableEmitter closedCompletableEmitter) {
        this.myChannel = myChannel;
        this.userAgent = userAgent;
        this.myid = myid;
        this.endpoint = endpoint;
        this.isClosed = isClosed;
        this.emitters = emitters;
        this.closedCompletable = closedCompletable;
        this.closedCompletableEmitter = closedCompletableEmitter;
    }

    /**
     * Returns the channel close future.
     */
    public ChannelFuture getCloseFuture() {
        return myChannel.closeFuture();
    }

    @Override
    public void send(IMessage message) {
        if (message != null && !isClosed && myChannel.isOpen()) {
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
            if (isClosed) {
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
        return this.myid;
    }

    @Override
    public void close() {
        if (!isClosed) {
            isClosed = true;
            myChannel.flush();
            emitters.clear();
            myChannel.writeAndFlush(new Leave())
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public Completable isClosed() {
        return closedCompletable;
    }

    @Override
    public String getConnectionId() {
        return myChannel.id().asLongText();
    }

    @Override
    public int hashCode() {
        return Objects.hash(myid);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ServerSession) {
            ServerSession c2 = (ServerSession) o;

            return Objects.equals(getConnectionId(), c2.getConnectionId());
        }

        return false;
    }

    @Override
    public String toString() {
        return MessageFormat.format("[S:{0}/C:{1}]", myid, getConnectionId());
    }

    /**
     * Returns the correct logger. Is needed for sub-classes.
     */
    protected Logger getLogger() {
        return LOG;
    }
}
