/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli;

import org.drasyl.annotation.NonNull;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Run a drasyl node.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
@Command(
        name = "node",
        header = { "Runs a drasyl node", "Can, for example, be used to operate a super peer" }
)
public class NodeCommand extends GlobalOptions implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(NodeCommand.class);
    protected final PrintStream out;
    private final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier;
    @Option(
            names = { "-c", "--config" },
            description = "Loads the node configuration from specified file. If the file does not exist, the default config will be used.",
            paramLabel = "<file>",
            defaultValue = "drasyl.conf"
    )
    private final File configFile;

    NodeCommand(final PrintStream out,
                final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier) {
        this.logLevel = null;
        this.configFile = null;
        this.out = requireNonNull(out);
        this.nodeSupplier = requireNonNull(nodeSupplier);
    }

    @SuppressWarnings("unused")
    public NodeCommand() {
        this(
                System.out, // NOSONAR
                // NOSONAR
                config -> {
                    try {
                        final CompletableFuture<Void> running = new CompletableFuture<>();
                        final DrasylNode myNode = new DrasylNode(config) {
                            @Override
                            public void onEvent(final @NonNull Event event) {
                                LOG.info("Event received: {}", event);
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
                        };
                        myNode.start();
                        return Pair.of(myNode, running);
                    }
                    catch (final DrasylException e) {
                        return Pair.of(null, failedFuture(e));
                    }
                }
        );
    }

    @Override
    public void run() {
        setLogLevel();

        DrasylNode node = null;
        try {
            final DrasylConfig config = getDrasylConfig();
            final Pair<DrasylNode, CompletableFuture<Void>> pair = nodeSupplier.apply(config);
            node = pair.first();
            final CompletableFuture<Void> running = pair.second();

            final DrasylNode finalNode = node;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (finalNode != null) {
                    LOG.info("Shutdown drasyl node.");
                    finalNode.shutdown().join();
                }
            }));

            // block while node is running
            running.join();
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }
        }
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
}
