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
package org.drasyl.cli.node;

import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.drasyl.annotation.NonNull;
import org.drasyl.cli.CliException;
import org.drasyl.cli.GlobalOptions;
import org.drasyl.cli.node.ActivityPattern.Activity;
import org.drasyl.cli.node.channel.NodeRcJsonRpc2OverHttpServerInitializer;
import org.drasyl.cli.node.channel.NodeRcJsonRpc2OverTcpServerInitializer;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.DrasylNodeSharedEventLoopGroupHolder;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.node.JsonUtil.JACKSON_MAPPER;

/**
 * Run a drasyl node.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
@Command(
        name = "node",
        header = { "Run a drasyl node.", "Can, for example, be used to operate a super peer" }
)
public class NodeCommand extends GlobalOptions implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(NodeCommand.class);
    @Option(
            names = { "-c", "--config" },
            description = "Loads the node configuration from specified file. If the file does not exist, the default config will be used.",
            paramLabel = "<file>",
            defaultValue = "drasyl.conf"
    )
    private File configFile;
    @Option(
            names = { "--activity-pattern" },
            description = "If supplied, the node will perform the given activities (e.g., send message, sleep, loop, etc.) specified in the file once started.",
            paramLabel = "<file>"
    )
    private File sendPatternFile;
    @ArgGroup
    private RemoteControl rc;
    @Option(
            names = { "--rc-bind" },
            description = "Binds remote control server to given IP and port. If no port is specified, a random free port will be used.",
            paramLabel = "<host>[:<port>]",
            defaultValue = "0.0.0.0:25421"
    )
    protected InetSocketAddress rcBindAddress;
    @Option(
            names = { "--rc-events-buffer-size" },
            description = {
                    "Maximum number of events that the buffer can hold.",
                    "On overflow, new events will push oldest events out of the buffer.",
                    "A value of 0 disables the limit (can lead to out of memory error)."
            },
            paramLabel = "<count>",
            defaultValue = "1000"
    )
    protected int rcEventsBufferSize;

    @SuppressWarnings({ "java:S138", "java:S1188", "java:S3776" })
    @Override
    public Integer call() throws DrasylException {
        setLogLevel();
        final List<Activity> sendPatternActivities = getActivities();

        DrasylNode node = null;
        Channel rcChannel = null;
        try {
            final DrasylConfig config = getDrasylConfig();

            final Queue<Event> events = new ArrayDeque<>();
            final CompletableFuture<Void> running = new CompletableFuture<>();
            node = new DrasylNode(config) {
                @Override
                public void onEvent(final @NonNull Event event) {
                    LOG.info("Event received: {}", event);
                    if (rc != null) {
                        while (rcEventsBufferSize > 0 && events.size() >= rcEventsBufferSize) {
                            events.poll();
                        }
                        events.add(event);
                    }
                    else {
                        if (event instanceof NodeNormalTerminationEvent) {
                            running.complete(null);
                        }
                        else if (event instanceof InboundExceptionEvent) {
                            ((InboundExceptionEvent) event).getError().printStackTrace(System.err); // NOSONAR
                        }
                        else if (event instanceof NodeUnrecoverableErrorEvent) {
                            running.completeExceptionally(((NodeUnrecoverableErrorEvent) event).getError());
                        }
                    }
                }
            };

            if (rc != null) {
                final NodeRcJsonRpc2OverTcpServerInitializer channelInitializer;
                if (rc.rcTcpJsonRpc) {
                    channelInitializer = new NodeRcJsonRpc2OverTcpServerInitializer(node, events);
                }
                else {
                    channelInitializer = new NodeRcJsonRpc2OverHttpServerInitializer(node, events);
                }
                final ServerBootstrap rcBootstrap = new ServerBootstrap()
                        .group(DrasylNodeSharedEventLoopGroupHolder.getParentGroup(), DrasylNodeSharedEventLoopGroupHolder.getChildGroup())
                        .channel(NioServerSocketChannel.class)
                        .childHandler(channelInitializer);
                rcChannel = rcBootstrap.bind(rcBindAddress).syncUninterruptibly().channel();
                LOG.info("Started remote control server listening on tcp:/{}", rcChannel.localAddress());
            }
            else {
                node.start().toCompletableFuture().exceptionally(e -> {
                    running.completeExceptionally(e);
                    return null;
                });
            }

            if (sendPatternActivities != null) {
                node.pipeline().addLast(new ActivityPatternHandler(sendPatternActivities));
            }

            final DrasylNode finalNode = node;
            final Channel finalRcChannel = rcChannel;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (finalRcChannel != null && finalRcChannel.isOpen()) {
                    LOG.info("Shutdown remote control server.");
                    finalRcChannel.close().syncUninterruptibly();
                }
                LOG.info("Shutdown drasyl node.");
                finalNode.shutdown().toCompletableFuture().join();
            }));

            // block while node is running (only completed if rc is disabled)
            running.join();

            return 0;
        }
        finally {
            if (rcChannel != null) {
                rcChannel.close().syncUninterruptibly();
            }
            if (node != null) {
                node.shutdown().toCompletableFuture().join();
            }
        }
    }

    private List<Activity> getActivities() {
        final List<Activity> sendPatternActivities;
        if (sendPatternFile != null) {
            try {
                JACKSON_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
                sendPatternActivities = JACKSON_MAPPER.readValue(sendPatternFile, new TypeReference<>() {
                });
            }
            catch (final IOException e) {
                throw new CliException("Unable to read activity pattern:", e);
            }
        }
        else {
            sendPatternActivities = null;
        }
        return sendPatternActivities;
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    /**
     * Tries to load a {@link DrasylConfig} from file defined in {@link #configFile}. If the does
     * not exist, the default configuration will be used.
     */
    protected DrasylConfig getDrasylConfig() {
        if (configFile.exists()) {
            log().info("Using config file from `{}`.", configFile);
            return DrasylConfig.parseFile(configFile);
        }
        else {
            log().info("Config file `{}` not found - using defaults.", configFile);
            return DrasylConfig.of();
        }
    }

    static class RemoteControl {
        @Option(
                names = "--rc-jsonrpc-tcp",
                description = {
                        "Starts a JSON-RPC 2.0 over TCP server listening on remote requests.",
                        "If this option is set, the node needs to be started manually.",
                        "Available methods: start, shutdown, send, identity, events"
                }
        )
        boolean rcTcpJsonRpc;
        @Option(
                names = "--rc-jsonrpc-http",
                description = {
                        "Starts a JSON-RPC 2.0 over HTTP server listening on remote requests.",
                        "If this option is set, the node needs to be started manually.",
                        "Available methods: start, shutdown, send, identity, events"
                }
        )
        boolean rcTcpJsonHttp;
    }
}
