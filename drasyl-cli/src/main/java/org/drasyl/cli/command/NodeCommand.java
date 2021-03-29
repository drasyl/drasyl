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
package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.cli.CliException;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Run a drasyl node.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NodeCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(NodeCommand.class);
    private final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier;
    private DrasylNode node;

    public NodeCommand() {
        this(
                System.out, // NOSONAR
                System.err, // NOSONAR
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
                },
                null
        );
    }

    NodeCommand(final PrintStream out,
                final PrintStream err,
                final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier,
                final DrasylNode node) {
        super(out, err);
        this.nodeSupplier = requireNonNull(nodeSupplier);
        this.node = node;
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @SuppressWarnings({ "java:S1192" })
    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "node",
                "Run a drasyl node in the current directory."
        );
    }

    @Override
    public void execute(final CommandLine cmd) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (node != null) {
                LOG.info("Shutdown drasyl node.");
                node.shutdown().join();
            }
        }));

        try {
            final DrasylConfig config = getDrasylConfig(cmd);
            final Pair<DrasylNode, CompletableFuture<Void>> pair = nodeSupplier.apply(config);
            node = pair.first();
            final CompletableFuture<Void> running = pair.second();
            // block while node is running
            running.get();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }
        }
    }

    @Override
    public String getDescription() {
        return "Run a drasyl node.";
    }
}
