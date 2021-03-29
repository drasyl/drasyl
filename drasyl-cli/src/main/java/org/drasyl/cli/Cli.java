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

import org.drasyl.cli.command.Command;
import org.drasyl.cli.command.GenerateIdentityCommand;
import org.drasyl.cli.command.HelpCommand;
import org.drasyl.cli.command.NodeCommand;
import org.drasyl.cli.command.PerfCommand;
import org.drasyl.cli.command.VersionCommand;
import org.drasyl.cli.command.WormholeCommand;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Provides a command line interface with drasyl-related tools (run root node, generate identity,
 * etc.).
 */
@SuppressWarnings("SameParameterValue")
public class Cli {
    private static final Logger LOG = LoggerFactory.getLogger(Cli.class);
    public static final Map<String, Command> COMMANDS;
    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;

    static {
        COMMANDS = Map.of(
                "genidentity", new GenerateIdentityCommand(),
                "help", new HelpCommand(),
                "node", new NodeCommand(),
                "perf", new PerfCommand(),
                "version", new VersionCommand(),
                "wormhole", new WormholeCommand()
        );
    }

    private final Map<String, Command> myCommands;
    private final Consumer<Integer> exitSupplier;

    public Cli() {
        this(COMMANDS, System::exit); // NOSONAR
    }

    @SuppressWarnings("SameParameterValue")
    Cli(final Map<String, Command> myCommands,
        final Consumer<Integer> exitSupplier) {
        this.myCommands = requireNonNull(myCommands);
        this.exitSupplier = requireNonNull(exitSupplier);
    }

    public static void main(final String[] args) {
        new Cli().run(args);
    }

    public void run(String[] args) {
        final String commandName;
        if (args.length > 0 && !"--help".equals(args[0]) && !"-h".equals(args[0])) {
            commandName = args[0];
        }
        else {
            commandName = "help";
            args = new String[0];
        }

        try {
            final Command command = myCommands.get(commandName);
            if (command != null) {
                command.execute(args);
                exitSupplier.accept(EXIT_SUCCESS);
            }
            else {
                throw new CliException("Unknown command \"" + commandName + "\" for \"drasyl\".");
            }
        }
        catch (final CliException e) {
            LOG.error(e);
            exitSupplier.accept(EXIT_FAILURE);
        }
    }
}
