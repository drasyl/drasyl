/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.server.session;

import city.sane.relay.common.messages.Message;
import city.sane.relay.common.messages.RelayException;
import city.sane.relay.common.messages.Response;
import city.sane.relay.common.models.Pair;
import city.sane.relay.common.models.SessionUID;
import city.sane.relay.server.util.listener.IResponseListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@link Session} object represents a connection to another remote system.
 * It can represent a remote system or a peer connection to another relay.
 *
 * @author Kevin
 */
@SuppressWarnings({"squid:S00107", "squid:CommentedOutCodeLine"})
public class Session {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    protected final ConcurrentHashMap<String, Pair<Class<? extends Message>, CompletableFuture<Message>>> futures;
    protected final List<IResponseListener<Message>> listeners;
    protected final long defaultFutureTimeout;
    protected final SessionUID myRelayUID;
    protected final Session self = this; //NOSONAR
    protected final Channel myChannel;
    protected final String userAgent;
    protected final SessionUID uid;
    protected final long bootTime;

    protected final AtomicLong totalFailedMessages;
    protected final AtomicLong totalSentMessages;
    protected final AtomicLong totalReceivedMessages;
    private final AtomicLong futuresTimeouted;
    private final URI targetSystem;

    protected volatile boolean isTerminated;
    protected volatile boolean isClosed;

    Session(ConcurrentHashMap<String, Pair<Class<? extends Message>, CompletableFuture<Message>>> futures,
            List<IResponseListener<Message>> listeners, long defaultFutureTimeout, SessionUID myRelayUID,
            Channel myChannel, String userAgent, SessionUID uid, long bootTime,
            AtomicLong totalFailedMessages, AtomicLong totalSentMessages, AtomicLong totalReceivedMessages,
            AtomicLong futuresTimeouted, URI targetSystem, boolean isTerminated, boolean isClosed) {
        this.futures = futures;
        this.listeners = listeners;
        this.defaultFutureTimeout = defaultFutureTimeout;
        this.myRelayUID = myRelayUID;
        this.myChannel = myChannel;
        this.userAgent = userAgent;
        this.uid = uid;
        this.bootTime = bootTime;
        this.totalFailedMessages = totalFailedMessages;
        this.totalSentMessages = totalSentMessages;
        this.totalReceivedMessages = totalReceivedMessages;
        this.futuresTimeouted = futuresTimeouted;
        this.targetSystem = targetSystem;
        this.isTerminated = isTerminated;
        this.isClosed = isClosed;
    }

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel              channel of the connection
     * @param targetSystem         the ip address and port of the target system
     * @param uid                  the UID of this {@link Session}
     * @param myRelayUID           the UID of this relay server
     * @param defaultFutureTimeout the default timeout for {@link java.util.concurrent.Future}s
     */
    public Session(Channel channel, URI targetSystem, SessionUID uid, SessionUID myRelayUID,
                   long defaultFutureTimeout) {
        this(channel, targetSystem, uid, myRelayUID, defaultFutureTimeout, "U/A");
    }

    /**
     * Creates a new connection.
     *
     * @param channel              channel of the connection
     * @param targetSystem         the ip address and port of the target system
     * @param uid                  the UID of this {@link Session}
     * @param myRelayUID           the UID of this relay server
     * @param defaultFutureTimeout the default timeout for {@link java.util.concurrent.Future}s
     * @param userAgent            the User-Agent string
     */
    public Session(Channel channel, URI targetSystem, SessionUID uid, SessionUID myRelayUID,
                   long defaultFutureTimeout, String userAgent) {
        this(new ConcurrentHashMap<>(), Collections.synchronizedList(new ArrayList<>()), defaultFutureTimeout,
                myRelayUID, channel, userAgent, uid, System.currentTimeMillis(), new AtomicLong(), new AtomicLong(),
                new AtomicLong(), new AtomicLong(), targetSystem, false, false);

        myChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                String msg = "The client and its associated channel";
                if (future.isSuccess()) {
                    LOG.debug("{} {} {} have been closed successfully.", self, msg, future.channel().id());
                } else {
                    LOG.error("{} {} {} could not be closed: ", self, msg, future.channel().id(), future.cause());
                }

                close();
            }
        });
    }

    /**
     * Returns the User-Agent.
     */
    public String getUserAgent() {
        return this.userAgent;
    }

    /**
     * Sends an exception to the remote host without awaiting any response.
     *
     * @param exception exception that should be sent
     */
    public void sendException(RelayException exception) {
        send(exception);
    }

    /**
     * Sends an exception in as a response to the remote host.
     *
     * @param exception exception that should be sent
     */
    public void sendExceptionAsResponse(RelayException exception, String messageID) {
        send(new Response<>(exception, messageID));
    }

    /**
     * Sends a message to the remote host without any response.
     *
     * @param message message that should be sent
     */
    public void sendMessage(Message message) {
        if (isClosed) {
            LOG.debug("This session has already been prompted to close. No further messages can be sent.");
        } else {
            send(message);
        }
    }

    /**
     * Sends a message to the remote host.
     *
     * @param <T>           the type of the response
     * @param message       message that should be sent
     * @param timeout       timeout in milliseconds before which the message must
     *                      have been sent and a response received
     * @param responseClass the class of the response object, to avoid
     *                      ClassCastExceptions
     * @return a future that can be fulfilled with a {@link Message response} to the
     * message
     */
    @SuppressWarnings("unchecked")
    public <T extends Message> CompletableFuture<T> sendMessageWithResponse(Message message, long timeout,
                                                                            Class<T> responseClass) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        if (isClosed || isTerminated) {
            future.cancel(true);
            LOG.debug("This object has already been prompted to close. No further messages can be sent.");
        } else {
            futures.putIfAbsent(message.getMessageID(), Pair.of(responseClass, future));

            future.orTimeout(timeout, TimeUnit.MILLISECONDS).whenComplete((m, error) -> {
                if (error != null && futures.contains(message.getMessageID())) {
                    futures.remove(message.getMessageID());
                    futuresTimeouted.incrementAndGet();
                }
            });

            send(message);
        }

        return (CompletableFuture<T>) future;
    }

    /**
     * Sends a message to the remote host.
     *
     * @param <T>           the type of the response
     * @param message       message that should be sent
     * @param responseClass the class of the response object, to avoid
     *                      ClassCastExceptions
     * @return a future that can be fulfilled with a {@link Message response} to the
     * message
     */
    public <T extends Message> CompletableFuture<T> sendMessageWithResponse(Message message, Class<T> responseClass) {
        return sendMessageWithResponse(message, defaultFutureTimeout, responseClass);
    }

    /**
     * Sets the result of a future from a {@link #sendMessageWithResponse(Message, Class)}
     * or {@link #sendMessageWithResponse(Message, long, Class)} call.
     *
     * @param msgID  the corresponding message ID
     * @param result the response
     */
    public void setResult(String msgID, Message result) {
        Pair<Class<? extends Message>, CompletableFuture<Message>> futurePair = futures.get(msgID);

        if (futurePair != null && futurePair.getLeft().isInstance(result) && futurePair.getRight() != null) {
            futurePair.getRight().complete(result);
            futures.remove(msgID);
        }
    }

    /**
     * Registers a {@link IResponseListener} listener on the session.
     *
     * @param listener Listener to be called at an event
     */
    public void addListener(IResponseListener<Message> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a {@link IResponseListener} listener.
     *
     * @param listener Listener to be removed
     */
    public void removeListener(IResponseListener<Message> listener) {
        listeners.remove(listener);
    }

    public SessionUID getUID() {
        return this.uid;
    }

    /**
     * @return the target system
     */
    public URI getTargetSystem() {
        return targetSystem;
    }

    /**
     * @return the boot timestamp
     */
    public long getBootTime() {
        return bootTime;
    }

    /**
     * @return true if the session is terminated/dead
     */
    public boolean isTerminated() {
        return isTerminated;
    }

    /**
     * @return the amount of pending futures
     */
    public long pendingFutures() {
        synchronized (futures) {
            return futures.size();
        }
    }

    /**
     * @return the amount of futures that have time outed
     */
    public long timeoutedFutures() {
        return futuresTimeouted.get();
    }

    /**
     * Handles incoming messages and notifies listeners.
     *
     * @param message incoming message
     */
    public void receiveMessage(Message message) {
        totalReceivedMessages.incrementAndGet();
        LOG.debug("[{} <= {}] {}", myRelayUID, uid, message);
        if (isTerminated)
            return;

        notifyListeners(message);
    }

    /**
     * Informs all registered listeners about an event.
     *
     * @param message Message to be informed about
     */
    protected void notifyListeners(Message message) {
        synchronized (listeners) {
            listeners.forEach(listener -> listener.emitEvent(message));
        }
    }

    /**
     * Sends a message to the remote host.
     *
     * @param message message that should be sent
     */
    protected void send(Message message) {
        if (message != null && !isTerminated && myChannel.isOpen()) {
            myChannel.writeAndFlush(message);
            totalSentMessages.incrementAndGet();
            LOG.debug("[{} => {}] {}", myRelayUID, uid, message);
            /* See: https://github.com/netty/netty/issues/7423
                    .addListener(future -> {
                System.err.println("bla");
                if (future.isSuccess()) {
                    totalSendMessages.getAndIncrement();
                    LOG.debug("[{} => {}] {}", myRelayUID, uid, message);
                } else {
                    totalFailedMessages.getAndIncrement();
                    LOG.info("{} Can't send message {}:", self, message, future.cause());
                }
            });*/
        } else {
            totalFailedMessages.getAndIncrement();
            LOG.info("[{} Can't send message {}", self, message);
        }
    }

    /**
     * Causes the session to close. All messages that are currently still in the I/O
     * are still processed. No new messages can be sent or received after
     * this method has been called.
     */
    public void close() {
        if (!isTerminated) {
            isClosed = true;

            try {
                myChannel.flush();
                myChannel.close();
            } finally {
                isTerminated = true;
                freeMemory();
            }
        }
    }

    /**
     * Frees memory.
     */
    private void freeMemory() {
        futures.clear();
        listeners.clear();
    }

    public ChannelId getChannelId() {
        return myChannel.id();
    }

    @Override
    public String toString() {
        return MessageFormat.format("[S:{0}/C:{1}]", uid, getChannelId().asShortText());
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Session) {
            Session c2 = (Session) o;

            return Objects.equals(uid, c2.uid);
        }

        return false;
    }

    public long getTotalSentMessages() {
        return totalSentMessages.get();
    }

    public long getTotalFailedMessages() {
        return totalFailedMessages.get();
    }

    public long getTotalReceivedMessages() {
        return totalReceivedMessages.get();
    }

    /**
     * Returns the channel close future.
     */
    public ChannelFuture getCloseFuture() {
        return myChannel.closeFuture();
    }
}
