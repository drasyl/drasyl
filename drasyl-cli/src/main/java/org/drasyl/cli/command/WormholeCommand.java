package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylException;
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.DrasylFunction;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Inspired by https://github.com/warner/magic-wormhole.
 */
public class WormholeCommand extends AbstractCommand {
    private final Supplier<Scanner> scannerSupplier;
    private final DrasylFunction<PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    private final DrasylFunction<PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;

    public WormholeCommand() {
        this(
                System.out, // NOSONAR
                () -> new Scanner(System.in),
                SendingWormholeNode::new,
                ReceivingWormholeNode::new
        );
    }

    WormholeCommand(PrintStream printStream,
                    Supplier<Scanner> scannerSupplier,
                    DrasylFunction<PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier,
                    DrasylFunction<PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier) {
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
    protected void help(CommandLine cmd) {
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
    protected void execute(CommandLine cmd) throws CliException {
        List<String> argList = cmd.getArgList();
        if (argList.size() >= 2) {
            String subcommand = argList.get(1);
            switch (subcommand) {
                case "send":
                    send();
                    break;
                case "receive":
                    receive(argList);
                    break;
                default:
                    throw new CliException("Unknown command \"" + subcommand + "\" for \"drasyl wormhole\"");
            }
        }
        else {
            help(cmd);
        }
    }

    private void send() throws CliException {
        SendingWormholeNode node = null;
        try {
            // prepare node
            node = sendingNodeSupplier.apply(printStream);
            node.start();

            // obtain text
            printStream.print("Text to send: ");
            String text = scannerSupplier.get().nextLine();
            node.setText(text);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (DrasylException e) {
            throw new CliException("Unable to create/run sending wormhole node: " + e.getMessage());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            throw new CliException(e.getCause().getMessage());
        }
        finally {
            if (node != null) {
                node.shutdown();
            }
        }
    }

    private void receive(List<String> argList) throws CliException {
        ReceivingWormholeNode node = null;
        try {
            // prepare node
            node = receivingNodeSupplier.apply(printStream);
            node.start();

            // obtain code
            String code;
            if (argList.size() < 3) {
                printStream.print("Enter wormhole code: ");
                code = scannerSupplier.get().nextLine().strip();
            }
            else {
                code = argList.get(2).strip();
            }

            // request text
            CompressedPublicKey sender = CompressedPublicKey.of(code.substring(0, 66));
            String password = code.substring(66);
            node.requestText(sender, password);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (CryptoException | IllegalArgumentException e) {
            throw new CliException("Invalid wormhole code supplied: " + e.getMessage());
        }
        catch (StringIndexOutOfBoundsException e) {
            throw new CliException("Invalid wormhole code supplied: must be at least 64 characters long");
        }
        catch (DrasylException e) {
            throw new CliException("Unable to create/run receiving wormhole node: " + e.getMessage());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            throw new CliException(e.getCause().getMessage());
        }
        finally {
            if (node != null) {
                node.shutdown();
            }
        }
    }
}