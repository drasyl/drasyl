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
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.perf.PerfClientNode;
import org.drasyl.cli.command.perf.PerfServerNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ThrowingBiFunction;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;

/**
 * Inspired by <a href="https://iperf.fr/iperf-download.php">https://iperf.fr/iperf-download.php</a>.
 */
@SuppressWarnings("java:S1192")
public class PerfCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(PerfCommand.class);
    private static final int DEFAULT_MPS = 100;
    private static final int DEFAULT_TIME = 10;
    private static final int DEFAULT_SIZE = 850;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, PerfServerNode, DrasylException> serverNodeSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, PerfClientNode, DrasylException> clientNodeSupplier;
    private DrasylNode node;

    PerfCommand(final PrintStream out,
                final PrintStream err,
                final ThrowingBiFunction<DrasylConfig, PrintStream, PerfServerNode, DrasylException> serverNodeSupplier,
                final ThrowingBiFunction<DrasylConfig, PrintStream, PerfClientNode, DrasylException> clientNodeSupplier) {
        super(out, err);
        this.serverNodeSupplier = requireNonNull(serverNodeSupplier);
        this.clientNodeSupplier = requireNonNull(clientNodeSupplier);
    }

    PerfCommand(final PrintStream out) {
        this(
                out,
                System.err, // NOSONAR
                PerfServerNode::new,
                PerfClientNode::new
        );
    }

    public PerfCommand() {
        this(System.out); // NOSONAR
    }

    @Override
    public String getDescription() {
        return "Speed test tool.";
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Options getOptions() {
        final Options options = super.getOptions();

        final Option client = Option.builder("c").longOpt("client").hasArg().argName("publicKey").type(String.class).desc("Runs in client mode, connecting to <publicKey>. If not set, runs in server mode.").build();
        options.addOption(client);

        final Option mps = Option.builder().longOpt("mps").hasArg().argName("count").type(Number.class).desc("Client: Messages per second (default: " + DEFAULT_MPS + ")").build();
        options.addOption(mps);

        final Option time = Option.builder().longOpt("time").hasArg().argName("seconds").type(Number.class).desc("Client: Time in seconds to transmit for (default: " + DEFAULT_TIME + ")").build();
        options.addOption(time);

        final Option size = Option.builder().longOpt("size").hasArg().argName("bytes").type(Number.class).desc("Client: Messages size (default: " + DEFAULT_SIZE + ")").build();
        options.addOption(size);

        final Option direct = Option.builder().longOpt("direct").desc("Client: Wait for direct connection before performing the test").build();
        options.addOption(direct);

        final Option reverse = Option.builder().longOpt("reverse").desc("Client: Run in reverse mode (server sends, client receives)").build();
        options.addOption(reverse);

        return options;
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "perf",
                "Tool for measuring network performance. A specified number of messages per second of the desired\n" +
                        "size are sent to another node over a defined period of time. The amount of transferred data, the\n" +
                        "bitrate, as well as the number of lost and (out of order) delivered messages, are recorded.",
                "",
                Map.of()
        );
    }

    @Override
    protected void execute(final CommandLine cmd) {
        if (cmd.hasOption("client")) {
            client(cmd);
        }
        else {
            server(cmd);
        }
    }

    private void server(final CommandLine cmd) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (node != null) {
                LOG.info("Shutdown perf server node.");
                node.shutdown().join();
            }
        }));

        try {
            // prepare node
            node = serverNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            // wait for node to finish
            ((PerfServerNode) node).doneFuture().get();
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run perf server node.", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }
        }
    }

    private void client(final CommandLine cmd) {
        try {
            // prepare node
            node = clientNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            // obtain server address and test options
            final Object value = cmd.getParsedOptionValue("client");
            final CompressedPublicKey server = CompressedPublicKey.of(value.toString());

            final int time;
            if (cmd.getParsedOptionValue("time") != null) {
                time = ((Number) cmd.getParsedOptionValue("time")).intValue();
            }
            else {
                time = DEFAULT_TIME;
            }

            final int messagesPerSecond;
            if (cmd.getParsedOptionValue("mps") != null) {
                messagesPerSecond = ((Number) cmd.getParsedOptionValue("mps")).intValue();
            }
            else {
                messagesPerSecond = DEFAULT_MPS;
            }

            final int size;
            if (cmd.getParsedOptionValue("size") != null) {
                size = ((Number) cmd.getParsedOptionValue("size")).intValue();
            }
            else {
                size = DEFAULT_SIZE;
            }

            if (time < 1 || messagesPerSecond < 1 || size < 1) {
                throw new CliException("time, mps, and size must be greater than 0");
            }

            final boolean directConnection = cmd.hasOption("direct");
            final boolean reverse = cmd.hasOption("reverse");

            ((PerfClientNode) node).setTestOptions(server, time, messagesPerSecond, size, directConnection, reverse);

            // wait for node to finish
            ((PerfClientNode) node).doneFuture().get();
        }
        catch (final IllegalArgumentException e) {
            throw new CliException("Invalid server address supplied", e);
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run perf client", e);
        }
        catch (final ParseException e) {
            throw new CliException("Unable to parse options", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }
        }
    }
}
