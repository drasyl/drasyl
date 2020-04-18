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

package city.sane.relay.server;

import city.sane.relay.common.messages.ForwardableMessage;
import city.sane.relay.common.messages.Message;
import city.sane.relay.common.messages.UserAgentMessage;
import city.sane.relay.common.models.SessionUID;
import city.sane.relay.server.session.Session;
import city.sane.relay.server.session.util.AutoDeletionBucket;
import city.sane.relay.server.session.util.ClientSessionBucket;
import city.sane.relay.server.session.util.MessageBucket;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings({"squid:S00107"})
public class RelayServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(RelayServer.class);
    public final ServerBootstrap serverBootstrap;
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    private final AutoDeletionBucket<SessionUID> newClientsBucket;
    private final AutoDeletionBucket<Session> deadClientsBucket;
    private final ClientSessionBucket clientSessionBucket;
    private final MessageBucket messageBucket;
    private final RelayServerConfig config;
    private final long bootTime;
    private final List<Runnable> beforeCloseListeners;
    private final CompletableFuture<Void> startedFuture;
    private final CompletableFuture<Void> stoppedFuture;
    private Channel serverChannel;
    private RelayBootstrap relayBootstrap;
    private boolean started;

    RelayServer(AutoDeletionBucket<SessionUID> newClientsBucket,
                ClientSessionBucket clientSessionBucket,
                MessageBucket messageBucket,
                RelayServerConfig config,
                long bootTime,
                Channel serverChannel,
                ServerBootstrap serverBootstrap,
                EventLoopGroup workerGroup,
                EventLoopGroup bossGroup,
                AutoDeletionBucket<Session> deadClientsBucket,
                List<Runnable> beforeCloseListeners,
                CompletableFuture<Void> startedFuture,
                CompletableFuture<Void> stoppedFuture,
                RelayBootstrap relayBootstrap, boolean started) {
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
        this.relayBootstrap = relayBootstrap;
        this.started = started;
    }

    /**
     * Relay server for forwarding messages to clients.
     *
     * @param config config that should be used
     */
    public RelayServer(RelayServerConfig config) throws RelayServerException {
        this(new AutoDeletionBucket<>(config.getRelayMaxHandoffTimeout().toMillis(),
                        config.getRelayMaxHandoffTimeout().toMillis()), new ClientSessionBucket(config.getRelayUID()),
                new MessageBucket(config.getRelayMsgBucketLimit()), config, System.currentTimeMillis(), null,
                new ServerBootstrap(), new NioEventLoopGroup(Math.min(1, ForkJoinPool.commonPool().getParallelism() - 1))
                , new NioEventLoopGroup(1), new AutoDeletionBucket<>(config.getDeadClientsSavingTime().toMillis(),
                        config.getDeadClientsSavingTime().toMillis() / 2), new ArrayList<>(), new CompletableFuture<>(),
                new CompletableFuture<>(), null, false);

        overrideUA();

        relayBootstrap = new RelayBootstrap(this, serverBootstrap, config);

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
    public RelayServer() throws RelayServerException, URISyntaxException {
        this(ConfigFactory.load());
    }

    public RelayServer(Config config) throws RelayServerException, URISyntaxException {
        this(new RelayServerConfig(config));
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
     * @return the entrypoint
     */
    public URI getEntrypoint() {
        return config.getRelayEntrypoint();
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
    public RelayServerConfig getConfig() {
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

    /**
     * Overrides the default UA of the
     * {@link Message Message} object.
     */
    @SuppressWarnings({"squid:S2696"})
    private void overrideUA() {
        UserAgentMessage.userAgentGenerator = () -> UserAgentMessage.defaultUserAgentGenerator.get() + " " + config.getRelayUserAgent();
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
    public synchronized void open() throws RelayServerException {
        if (started)
            throw new RelayServerException("Server is already started!");

        started = true;
        new Thread(this::openServerChannel).start();
    }

    void openServerChannel() {
        try {
            serverChannel = relayBootstrap.getChannel();
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
    public void awaitOpen() throws RelayServerException {
        try {
            startedFuture.get();
        } catch (InterruptedException e) {
            LOG.warn("Thread got interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RelayServerException(e.getCause());
        }
    }

    /**
     * Wait till relay server is stopped.
     */
    public void awaitClose() throws RelayServerException {
        try {
            stoppedFuture.get();
        } catch (InterruptedException e) {
            LOG.warn("Thread got interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RelayServerException(e.getCause());
        }
    }

    public static void startMonitoringServer(RelayServer relay) throws RelayServerException {
        try {
            if (relay.getConfig().isRelayMonitoringEnabled()) {
                MonitoringServer monitoring = new MonitoringServer(relay);
                monitoring.start();
                relay.beforeClose(monitoring::stop);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RelayServerException(e);
        }
    }

    /**
     * Starts a new relay server instance.
     */
    public static void main(String[] args) {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        try (RelayServer relay = new RelayServer()) {
            RelayServer.startMonitoringServer(relay);

            relay.open();
            relay.awaitClose();
        } catch (RelayServerException | URISyntaxException e) {
            LOG.error("", e);
        }
    }
}
