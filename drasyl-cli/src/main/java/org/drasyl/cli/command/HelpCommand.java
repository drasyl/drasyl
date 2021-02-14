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

import java.io.PrintStream;
import java.util.Map;
import java.util.stream.Collectors;

import static org.drasyl.cli.Cli.COMMANDS;

/**
 * Show help for drasyl commands and flags.
 */
public class HelpCommand extends AbstractCommand {
    public HelpCommand() {
        this(System.out, System.err); // NOSONAR
    }

    HelpCommand(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "help",
                String.format("drasyl is an general purpose transport overlay network.%n" +
                        "%n" +
                        "See the home page (https://drasyl.org/) for installation, usage,%n" +
                        "documentation, changelog and configuration walkthroughs.")
        );
    }

    @Override
    public void execute(final CommandLine cmd) {
        helpTemplate(
                "",
                "",
                "Use \"drasyl [command] --help\" for more information about a command.",
                COMMANDS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDescription()))
        );
    }

    @Override
    public String getDescription() {
        return "Show help for drasyl commands and flags.";
    }
}
