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
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.util.ThrowingBiFunction;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Inspired by <a href="https://github.com/warner/magic-wormhole">Magic Wormhole</a>.
 */
public class WormholeCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeCommand.class);
    private final Supplier<Scanner> scannerSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;

    WormholeCommand(final PrintStream out,
                    final PrintStream err,
                    final Supplier<Scanner> scannerSupplier,
                    final ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier,
                    final ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier) {
        super(out, err);
        this.scannerSupplier = requireNonNull(scannerSupplier);
        this.sendingNodeSupplier = requireNonNull(sendingNodeSupplier);
        this.receivingNodeSupplier = requireNonNull(receivingNodeSupplier);
    }

    WormholeCommand(final PrintStream out) {
        this(
                out,
                System.err, // NOSONAR
                () -> new Scanner(System.in), // NOSONAR
                SendingWormholeNode::new,
                ReceivingWormholeNode::new
        );
    }

    public WormholeCommand() {
        this(System.out); // NOSONAR
    }

    @Override
    public String getDescription() {
        return "Transfer a text message from one node to another, safely.";
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Options getOptions() {
        final Options options = super.getOptions();

        final Option client = Option.builder().longOpt("text").hasArg().argName("message").type(String.class).desc("Text message to send.").build();
        options.addOption(client);

        return options;
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "wormhole",
                "Transfer a text message from one node to another, safely.",
                "Use \"drasyl wormhole send\" to send a text message.",
                Map.of(
                        "send", "Send a text message.",
                        "receive", "Receive a text message (from \"drasyl wormhole send\")"
                )
        );
    }

    @Override
    protected void execute(final CommandLine cmd) {
        final List<String> argList = cmd.getArgList();
        if (argList.size() >= 2) { // NOSONAR
            final String subcommand = argList.get(1);
            switch (subcommand) {
                case "send":
                    send(cmd);
                    break;
                case "receive":
                    receive(cmd);
                    break;
                default:
                    err.println("ERR: Unknown command \"" + subcommand + "\" for \"drasyl wormhole\".");
            }
        }
        else {
            help(cmd);
        }
    }

    private void send(final CommandLine cmd) {
        SendingWormholeNode node = null;
        try {
            // prepare node
            node = sendingNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            final String text;
            if (!cmd.hasOption("text")) {
                // obtain text
                out.print("Text to send: ");
                text = scannerSupplier.get().nextLine();
            }
            else {
                text = cmd.getParsedOptionValue("text").toString();
            }
            node.setText(text);

            // wait for node to finish
            node.doneFuture().get();
            node.shutdown().join();
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run node", e);
        }
        catch (final ParseException e) {
            throw new CliException("Unable to parse options", e);
        }
        catch (final InterruptedException e) {
            LOG.info("Shutdown wormhole node.");
            node.shutdown().join();
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

    private void receive(final CommandLine cmd) {
        ReceivingWormholeNode node = null;
        try {
            // prepare node
            node = receivingNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            // obtain code
            final List<String> argList = cmd.getArgList();
            final String code;
            if (argList.size() < 3) { // NOSONAR
                out.print("Enter wormhole code: ");
                code = scannerSupplier.get().nextLine().strip();
            }
            else {
                code = argList.get(2).strip(); // NOSONAR
            }

            // request text
            if (code.length() < IdentityPublicKey.KEY_LENGTH_AS_STRING) {
                err.println("ERR: Invalid wormhole code supplied: must be at least " + IdentityPublicKey.KEY_LENGTH_AS_STRING + " characters long.");
                return;
            }

            final IdentityPublicKey sender = IdentityPublicKey.of(code.substring(0, IdentityPublicKey.KEY_LENGTH_AS_STRING));
            final String password = code.substring(IdentityPublicKey.KEY_LENGTH_AS_STRING);
            node.requestText(sender, password);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final IllegalArgumentException e) {
            throw new CliException("Invalid wormhole code supplied supplied", e);
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run node", e);
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
