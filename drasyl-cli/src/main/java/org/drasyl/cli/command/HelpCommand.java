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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.Map;
import java.util.stream.Collectors;

import static org.drasyl.cli.Cli.COMMANDS;

/**
 * Show help for drasyl commands and flags.
 */
public class HelpCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(HelpCommand.class);

    public HelpCommand() {
        this(System.out, System.err); // NOSONAR
    }

    HelpCommand(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    protected Logger log() {
        return LOG;
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
                String.format("Use \"drasyl [command] --help\" for more information about a command.%n" +
                        "%n" +
                        "The environment variable JAVA_OPTS can be used to pass options to the JVM."),
                COMMANDS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDescription()))
        );
    }

    @Override
    public String getDescription() {
        return "Show help for drasyl commands and flags.";
    }
}
