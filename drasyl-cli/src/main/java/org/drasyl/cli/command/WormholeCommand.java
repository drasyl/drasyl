/*
 * Copyright (c) 2020-2021.
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

package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ThrowingBiFunction;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Inspired by <a href="https://github.com/warner/magic-wormhole">https://github.com/warner/magic-wormhole</a>.
 */
public class WormholeCommand extends AbstractCommand {
    private final Supplier<Scanner> scannerSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;

    public WormholeCommand() {
        this(
                System.out, // NOSONAR
                () -> new Scanner(System.in),
                SendingWormholeNode::new,
                ReceivingWormholeNode::new
        );
    }

    WormholeCommand(final PrintStream printStream,
                    final Supplier<Scanner> scannerSupplier,
                    final ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier,
                    final ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier) {
        super(printStream);
        this.scannerSupplier = scannerSupplier;
        this.sendingNodeSupplier = sendingNodeSupplier;
        this.receivingNodeSupplier = receivingNodeSupplier;
    }

    @Override
    public String getDescription() {
        return "Transfer a text message from one node to another, safely.";
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
    protected void execute(final CommandLine cmd) throws CliException {
        final List<String> argList = cmd.getArgList();
        if (argList.size() >= 2) {
            final String subcommand = argList.get(1);
            switch (subcommand) {
                case "send":
                    send(cmd);
                    break;
                case "receive":
                    receive(cmd);
                    break;
                default:
                    throw new CliException("Unknown command \"" + subcommand + "\" for \"drasyl wormhole\"");
            }
        }
        else {
            help(cmd);
        }
    }

    private void send(final CommandLine cmd) throws CliException {
        SendingWormholeNode node = null;
        try {
            // prepare node
            node = sendingNodeSupplier.apply(getDrasylConfig(cmd), printStream);
            node.start();

            // obtain text
            printStream.print("Text to send: ");
            final String text = scannerSupplier.get().nextLine();
            node.setText(text);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run sending wormhole node", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e.getCause());
        }
        finally {
            if (node != null) {
                node.shutdown();
            }
        }
    }

    private void receive(final CommandLine cmd) throws CliException {
        ReceivingWormholeNode node = null;
        try {
            // prepare node
            node = receivingNodeSupplier.apply(getDrasylConfig(cmd), printStream);
            node.start();

            // obtain code
            final List<String> argList = cmd.getArgList();
            final String code;
            if (argList.size() < 3) {
                printStream.print("Enter wormhole code: ");
                code = scannerSupplier.get().nextLine().strip();
            }
            else {
                code = argList.get(2).strip();
            }

            // request text
            final CompressedPublicKey sender = CompressedPublicKey.of(code.substring(0, 66));
            final String password = code.substring(66);
            node.requestText(sender, password);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final IllegalArgumentException e) {
            throw new CliException("Invalid wormhole code supplied", e);
        }
        catch (final StringIndexOutOfBoundsException e) {
            throw new CliException("Invalid wormhole code supplied: must be at least 64 characters long");
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run receiving wormhole node", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e.getCause());
        }
        finally {
            if (node != null) {
                node.shutdown();
            }
        }
    }
}
