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
import org.drasyl.DrasylNode;

import java.io.PrintStream;

/**
 * Show the version number.
 */
public class VersionCommand extends AbstractCommand {
    public VersionCommand() {
        this(System.out, System.err); // NOSONAR
    }

    VersionCommand(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate("version", "Show the drasyl, os and java version number.");
    }

    @Override
    public void execute(final CommandLine cmd) {
        out.println("drasyl v" + DrasylNode.getVersion());
        out.println("- os.name " + System.getProperty("os.name"));
        out.println("- os.version " + System.getProperty("os.version"));
        out.println("- os.arch " + System.getProperty("os.arch"));
        out.println("- java.version " + System.getProperty("java.version"));
    }

    @Override
    public String getDescription() {
        return "Show the version number.";
    }
}
