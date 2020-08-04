/*
 * Copyright (c) 2020.
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

import org.drasyl.DrasylNode;
import org.drasyl.cli.command.Command;
import org.drasyl.cli.command.GenerateIdentityCommand;
import org.drasyl.cli.command.HelpCommand;
import org.drasyl.cli.command.NodeCommand;
import org.drasyl.cli.command.VersionCommand;
import org.drasyl.util.DrasylScheduler;

import java.util.Map;

/**
 * Provides a command line interface with drasyl-related tools (run root node, generate identity,
 * etc.).
 */
public class Cli {
    public static final Map<String, Command> COMMANDS;

    static {
        COMMANDS = Map.of(
                "genidentity", new GenerateIdentityCommand(),
                "help", new HelpCommand(),
                "node", new NodeCommand(),
                "version", new VersionCommand()
        );
    }

    private final Map<String, Command> myCommands;

    public Cli() {
        this(COMMANDS);
    }

    Cli(Map<String, Command> myCommands) {
        this.myCommands = myCommands;
    }

    public static void main(String[] args) throws CliException {
        Cli cli = new Cli();
        try {
            cli.run(args);
            DrasylNode.irrevocablyTerminate();
            DrasylScheduler.shutdown();
        }
        catch (CommandNotFoundCliException e) {
            System.out.println("Error: " + e.getMessage()); // NOSONAR
            System.exit(1);
        }
    }

    public void run(String[] args) throws CliException {
        String commandName;
        if (args.length > 0) {
            commandName = args[0];
        }
        else {
            commandName = "help";
        }

        Command command = myCommands.get(commandName);
        if (command == null) {
            throw new CommandNotFoundCliException(commandName);
        }
        command.execute(args);
    }
}