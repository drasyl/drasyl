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
package org.drasyl.core.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.UserAgentMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeerInformation;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@SuppressWarnings({ "squid:S00107" })
public class NodeServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NodeServer.class);
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    public final ServerBootstrap serverBootstrap;
    private final CompletableFuture<Void> startedFuture;
    private final CompletableFuture<Void> stoppedFuture;
    private final List<Runnable> beforeCloseListeners;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final DrasylNodeConfig config;
    private final Messenger messenger;
    private Channel serverChannel;
    private NodeServerBootstrap nodeServerBootstrap;
    private boolean started;
    private int actualPort;
    private Set<URI> actualEndpoints;

    /**
     * Starts a node server for forwarding messages to child peers.<br> Default Port: 22527
     */
    public NodeServer(IdentityManager identityManager,
                      PeersManager peersManager, Messenger messenger) throws DrasylException {
        this(identityManager, messenger, peersManager, ConfigFactory.load());
    }

    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      Config config) throws DrasylException {
        this(identityManager, messenger, peersManager, new DrasylNodeConfig(config));
    }

    /**
     * Node server for forwarding messages to child peers.
     *
     * @param identityManager the identity manager
     * @param messenger       the messenger object
     * @param peersManager    the peers manager
     * @param config          config that should be used
     */
    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      DrasylNodeConfig config) throws NodeServerException {
        this(identityManager, messenger, peersManager, config,
                null, new ServerBootstrap(),
                new NioEventLoopGroup(Math.min(1, ForkJoinPool.commonPool().getParallelism() - 1)), new NioEventLoopGroup(1), new ArrayList<>(),
                new CompletableFuture<>(), new CompletableFuture<>(), null, false, -1, new HashSet<>());

        overrideUA();

        nodeServerBootstrap = new NodeServerBootstrap(this, serverBootstrap, config);

        LOG.info("Started node server with the following configurations: \n {}", config);
    }

    NodeServer(IdentityManager identityManager,
               Messenger messenger,
               PeersManager peersManager,
               DrasylNodeConfig config,
               Channel serverChannel,
               ServerBootstrap serverBootstrap,
               EventLoopGroup workerGroup,
               EventLoopGroup bossGroup,
               List<Runnable> beforeCloseListeners,
               CompletableFuture<Void> startedFuture,
               CompletableFuture<Void> stoppedFuture,
               NodeServerBootstrap nodeServerBootstrap,
               boolean started,
               int actualPort,
               Set<URI> actualEndpoints) {
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.config = config;
        this.serverChannel = serverChannel;
        this.serverBootstrap = serverBootstrap;
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.beforeCloseListeners = beforeCloseListeners;
        this.startedFuture = startedFuture;
        this.stoppedFuture = stoppedFuture;
        this.nodeServerBootstrap = nodeServerBootstrap;
        this.started = started;
        this.messenger = messenger;
        this.actualPort = actualPort;
        this.actualEndpoints = actualEndpoints;
    }

    /**
     * Overrides the default UA of the {@link IMessage Message} object.
     */
    @SuppressWarnings({ "squid:S2696" })
    private void overrideUA() {
        UserAgentMessage.userAgentGenerator = () -> UserAgentMessage.defaultUserAgentGenerator.get() + " " + config.getUserAgent();
    }

    public void send(Message message) throws DrasylException {
        messenger.send(message);
    }

    /**
     * @return the peers manager
     */
    public PeersManager getPeersManager() {
        return peersManager;
    }

    /**
     * @return the entry points
     */
    public Set<URI> getEntryPoints() {
        return actualEndpoints;
    }

    /**
     * @return the config
     */
    public DrasylNodeConfig getConfig() {
        return config;
    }

    @SuppressWarnings({ "java:S1144" })
    private void beforeClose(Runnable listener) {
        beforeCloseListeners.add(listener);
    }

    EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    EventLoopGroup getWorkerGroup() {
        return workerGroup;
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
     * Wait till relay server is started and ready to accept connections.
     */
    public void awaitOpen() throws NodeServerException {
        try {
            startedFuture.get();
        }
        catch (InterruptedException e) {
            LOG.warn("Thread got interrupted", e);
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            throw new NodeServerException(e.getCause());
        }
    }

    public IdentityManager getMyIdentity() {
        return identityManager;
    }

    /**
     * Starts the relay server.
     */
    public synchronized void open() throws NodeServerException {
        if (started) {
            throw new NodeServerException("Server is already started!");
        }

        started = true;
        new Thread(this::openServerChannel).start();
    }

    void openServerChannel() {
        try {
            serverChannel = nodeServerBootstrap.getChannel();

            InetSocketAddress socketAddress = (InetSocketAddress) serverChannel.localAddress();
            actualPort = socketAddress.getPort();
            actualEndpoints = config.getServerEndpoints().stream()
                    .map(a -> URI.create(a.replace(":" + config.getServerBindPort(), ":" + getPort())))
                    .collect(Collectors.toSet());

            startedFuture.complete(null);
            serverChannel.closeFuture().sync();
        }
        catch (Exception e) {
            startedFuture.completeExceptionally(e);
            LOG.error("", e);
        }
        finally {
            close();
        }
    }

    /**
     * Returns the actual bind port used by this server.
     *
     * @return
     */
    public int getPort() {
        return actualPort;
    }

    /**
     * Closes the server socket and all open client sockets.
     */
    @Override
    public void close() {
        beforeCloseListeners.forEach(Runnable::run);

        Map<Identity, PeerInformation> localPeers = new HashMap<>(peersManager.getPeers());
        localPeers.keySet().retainAll(peersManager.getChildren());

        localPeers.forEach((id, peer) -> peer.getConnections().forEach(con -> {
            if (con != null) {
                con.close();
            }
        }));

        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.closeFuture().syncUninterruptibly();
        }

        stoppedFuture.complete(null);
    }

    /**
     * Wait till relay server is stopped.
     */
    public void awaitClose() throws NodeServerException {
        try {
            stoppedFuture.get();
        }
        catch (InterruptedException e) {
            LOG.warn("Thread got interrupted", e);
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            throw new NodeServerException(e.getCause());
        }
    }
}
