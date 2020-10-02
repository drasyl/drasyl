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
        this(System.out); // NOSONAR
    }

    HelpCommand(final PrintStream printStream) {
        super(printStream);
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