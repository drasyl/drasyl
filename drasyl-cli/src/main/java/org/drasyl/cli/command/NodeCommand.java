package org.drasyl.cli.command;

import io.sentry.Sentry;
import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Run a drasyl node.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NodeCommand extends AbstractCommand {
    private static final Logger log = LoggerFactory.getLogger(NodeCommand.class);

    static {
        Sentry.getStoredClient().setRelease(DrasylNode.getVersion());
    }

    private final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier;
    private DrasylNode node;

    public NodeCommand() {
        this(
                System.out, // NOSONAR
                config -> {
                    try {
                        CompletableFuture<Void> running = new CompletableFuture<>();
                        DrasylNode myNode = new DrasylNode(config) {
                            @Override
                            public void onEvent(Event event) {
                                log.info("Event received: {}", event);
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
                    catch (DrasylException e) {
                        return Pair.of(null, failedFuture(e));
                    }
                },
                null
        );
    }

    NodeCommand(PrintStream printStream,
                Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier,
                DrasylNode node) {
        super(printStream);
        this.nodeSupplier = nodeSupplier;
        this.node = node;
    }

    @SuppressWarnings({ "java:S1192" })
    @Override
    protected void help(CommandLine cmd) {
        helpTemplate(
                "node",
                "Run a drasyl node in the current directory."
        );
    }

    @Override
    public void execute(CommandLine cmd) throws CliException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (node != null) {
                log.info("Shutdown drasyl Node");
                node.shutdown().join();
            }
        }));

        try {
            DrasylConfig config = getDrasylConfig(cmd);
            Pair<DrasylNode, CompletableFuture<Void>> pair = nodeSupplier.apply(config);
            node = pair.first();
            CompletableFuture<Void> running = pair.second();
            running.get(); // block while node is running
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            throw new CliException(e);
        }
    }

    @Override
    public String getDescription() {
        return "Run a drasyl node.";
    }
}