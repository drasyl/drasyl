/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all;

import org.drasyl.all.messages.ForwardableMessage;
import org.drasyl.all.messages.Message;
import org.drasyl.all.messages.UserAgentMessage;
import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.SessionUID;
import org.drasyl.all.session.Session;
import org.drasyl.all.session.util.AutoDeletionBucket;
import org.drasyl.all.session.util.ClientSessionBucket;
import org.drasyl.all.session.util.MessageBucket;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings({"squid:S00107"})
public class Drasyl implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Drasyl.class);
    public final ServerBootstrap serverBootstrap;
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    private final AutoDeletionBucket<SessionUID> newClientsBucket;
    private final AutoDeletionBucket<Session> deadClientsBucket;
    private final ClientSessionBucket clientSessionBucket;
    private final MessageBucket messageBucket;
    private final DrasylConfig config;
    private final long bootTime;
    private final List<Runnable> beforeCloseListeners;
    private final CompletableFuture<Void> startedFuture;
    private final CompletableFuture<Void> stoppedFuture;
    private Channel serverChannel;
    private DrasylBootstrap drasylBootstrap;
    private boolean started;

    Drasyl(AutoDeletionBucket<SessionUID> newClientsBucket,
           ClientSessionBucket clientSessionBucket,
           MessageBucket messageBucket,
           DrasylConfig config,
           long bootTime,
           Channel serverChannel,
           ServerBootstrap serverBootstrap,
           EventLoopGroup workerGroup,
           EventLoopGroup bossGroup,
           AutoDeletionBucket<Session> deadClientsBucket,
           List<Runnable> beforeCloseListeners,
           CompletableFuture<Void> startedFuture,
           CompletableFuture<Void> stoppedFuture,
           DrasylBootstrap drasylBootstrap, boolean started) {
        this.newClientsBucket = newClientsBucket;
        this.clientSessionBucket = clientSessionBucket;
        this.messageBucket = messageBucket;
        this.config = config;
        this.bootTime = bootTime;
        this.serverChannel = serverChannel;
        this.serverBootstrap = serverBootstrap;
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.deadClientsBucket = deadClientsBucket;
        this.beforeCloseListeners = beforeCloseListeners;
        this.startedFuture = startedFuture;
        this.stoppedFuture = stoppedFuture;
        this.drasylBootstrap = drasylBootstrap;
        this.started = started;
    }

    /**
     * Relay server for forwarding messages to clients.
     *
     * @param config config that should be used
     */
    public Drasyl(DrasylConfig config) throws DrasylException {
        this(new AutoDeletionBucket<>(config.getRelayMaxHandoffTimeout().toMillis(),
                        config.getRelayMaxHandoffTimeout().toMillis()), new ClientSessionBucket(config.getRelayUID()),
                new MessageBucket(config.getRelayMsgBucketLimit()), config, System.currentTimeMillis(), null,
                new ServerBootstrap(), new NioEventLoopGroup(Math.min(1, ForkJoinPool.commonPool().getParallelism() - 1))
                , new NioEventLoopGroup(1), new AutoDeletionBucket<>(config.getDeadClientsSavingTime().toMillis(),
                        config.getDeadClientsSavingTime().toMillis() / 2), new ArrayList<>(), new CompletableFuture<>(),
                new CompletableFuture<>(), null, false);

        drasylBootstrap = new DrasylBootstrap(this, serverBootstrap, config);

        if (config.isRelayDebugging()) {
            System.err.println("Attention DEBUGGING Mode is activated!"); // NOSONAR
        }

        LOG.info("Started relay server with the following configurations: \n {}", config);
    }

    /**
     * Starts a relay server for forwarding messages to clients.<br>
     * Default Port: 22527<br>
     * Default Channel: default<br>
     */
    public Drasyl() throws DrasylException {
        this(ConfigFactory.load());
    }

    public Drasyl(Config config) throws DrasylException {
        this(new DrasylConfig(config));
    }

    /**
     * @return the client bucket
     */
    public ClientSessionBucket getClientBucket() {
        return clientSessionBucket;
    }

    /**
     * @return the message bucket
     */
    public MessageBucket getMessageBucket() {
        return messageBucket;
    }

    /**
     * @return the bucket that contains the session UIDs of newly joined clients
     */
    public AutoDeletionBucket<SessionUID> getNewClientsBucket() {
        return newClientsBucket;
    }

    /**
     * Sends the given message to all given locally connected clients, in parallel
     * threads.
     *
     * @param message             the message to send
     * @param localClientSessions map of receivers and their client UIDs
     */
    public void broadcastMessageLocally(ForwardableMessage message, Map<SessionUID, Session> localClientSessions) {
        Map<SessionUID, Session> clientSessionsCopy = Maps.newHashMap(localClientSessions);
        clientSessionsCopy.keySet().retainAll(clientSessionBucket.getLocalClientUIDs());
        if (!clientSessionsCopy.isEmpty()) {
            clientSessionsCopy.forEach((key, value) -> forwardMessage(message, key, value));
        }
    }

    /**
     * Replaces the {@link ForwardableMessage#getReceiverUID()} receiverUID} of the message
     * with the given clientUID and sends it to the given client.
     *
     * @param message   the message to send
     * @param clientUID the session UID of the receiver
     * @param client    the receiver
     */
    public void forwardMessage(ForwardableMessage message, SessionUID clientUID, Session client) {
        if (clientSessionBucket.getLocalClientSessions().contains(client)) {
            try {
                client.sendMessage(ForwardableMessage.to(message, clientUID));
            } catch (IllegalStateException e) {
                LOG.error("Message could not be forwarded to client with UID {}: {}.", clientUID, e);
            }
        }
    }

    /**
     * Closes the server socket and all open client sockets.
     */
    @Override
    public void close() {
        beforeCloseListeners.forEach(Runnable::run);

        clientSessionBucket.getLocalClientSessions().forEach(c -> {
            if (c != null) {
                c.close();
            }
        });

        newClientsBucket.cancelTimer();
        deadClientsBucket.cancelTimer();

        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.closeFuture().syncUninterruptibly();
        }

        stoppedFuture.complete(null);
    }

    /**
     * @return the externalIP
     */
    public IPAddress getExternalIP() {
        return new IPAddress(config.getRelayExternalIP(), config.getRelayPort());
    }

    /**
     * return the port
     *
     * @return the port
     */
    public int getPort() {
        return config.getRelayPort();
    }

    /**
     * Returns the {@link SessionUID} of this server instance.
     *
     * @return UID of this server
     */
    public SessionUID getUID() {
        return config.getRelayUID();
    }

    /**
     * @return the config
     */
    public DrasylConfig getConfig() {
        return config;
    }

    /**
     * @return the boot timestamp
     */
    public long getBootTime() {
        return bootTime;
    }

    private void beforeClose(Runnable listener) {
        beforeCloseListeners.add(listener);
    }

    EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public AutoDeletionBucket<Session> getDeadClientsBucket() {
        return deadClientsBucket;
    }

    public CompletableFuture<Void> getStartedFuture() {
        return startedFuture;
    }

    public CompletableFuture<Void> getStoppedFuture() {
        return stoppedFuture;
    }

    public boolean getStarted() {
        return started;
    }

    /**
     * Starts the relay server.
     */
    public synchronized void open() throws DrasylException {
        if (started)
            throw new DrasylException("Server is already started!");

        started = true;
        new Thread(this::openServerChannel).start();
    }

    void openServerChannel() {
        try {
            serverChannel = drasylBootstrap.getChannel();
            startedFuture.complete(null);
            serverChannel.closeFuture().sync();
        } catch (Exception e) {
            startedFuture.completeExceptionally(e);
            LOG.error("", e);
        } finally {
            close();
        }
    }

    /**
     * Wait till relay server is started and ready to accept connections.
     */
    public void awaitOpen() throws DrasylException {
        try {
            startedFuture.get();
        } catch (InterruptedException e) {
            LOG.warn("Thread got interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new DrasylException(e.getCause());
        }
    }

    /**
     * Wait till relay server is stopped.
     */
    public void awaitClose() throws DrasylException {
        try {
            stoppedFuture.get();
        } catch (InterruptedException e) {
            LOG.warn("Thread got interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new DrasylException(e.getCause());
        }
    }

    public static void startMonitoringServer(Drasyl relay) throws DrasylException {
        try {
            if (relay.getConfig().isRelayMonitoringEnabled()) {
                MonitoringServer monitoring = new MonitoringServer(relay);
                monitoring.start();
                relay.beforeClose(monitoring::stop);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DrasylException(e);
        }
    }

    /**
     * Starts a new relay server instance.
     */
    public static void main(String[] args) {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        try (Drasyl relay = new Drasyl()) {
            Drasyl.startMonitoringServer(relay);

            relay.open();
            relay.awaitClose();
        } catch (DrasylException e) {
            LOG.error("", e);
        }
    }
}
