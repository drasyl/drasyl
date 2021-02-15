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
package org.drasyl.cli;

import org.drasyl.cli.command.Command;
import org.drasyl.cli.command.GenerateIdentityCommand;
import org.drasyl.cli.command.HelpCommand;
import org.drasyl.cli.command.NodeCommand;
import org.drasyl.cli.command.PerfCommand;
import org.drasyl.cli.command.VersionCommand;
import org.drasyl.cli.command.WormholeCommand;

import java.io.PrintStream;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Provides a command line interface with drasyl-related tools (run root node, generate identity,
 * etc.).
 */
@SuppressWarnings("SameParameterValue")
public class Cli {
    public static final Map<String, Command> COMMANDS;

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

    private final PrintStream err;
    private final Map<String, Command> myCommands;

    public Cli() {
        this(System.err, COMMANDS); // NOSONAR
    }

    @SuppressWarnings("SameParameterValue")
    Cli(final PrintStream err,
        final Map<String, Command> myCommands) {
        this.err = requireNonNull(err);
        this.myCommands = requireNonNull(myCommands);
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

        final Command command = myCommands.get(commandName);
        if (command != null) {
            command.execute(args);
        }
        else {
            err.println("ERR: Unknown command \"" + commandName + "\" for \"drasyl\".");
        }
    }
}
