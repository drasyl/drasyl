package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;

import static org.drasyl.cli.Cli.COMMANDS;

public class HelpCommand extends AbstractCommand {
    public HelpCommand() {
        this(System.out); // NOSONAR
    }

    HelpCommand(PrintStream printStream) {
        super(printStream);
    }

    @Override
    protected void help(CommandLine cmd) {
        helpTemplate(
                "help",
                "drasyl is an general purpose transport overlay network.\n" +
                        "\n" +
                        "See the home page (https://drasyl.org/) for installation, usage,\n" +
                        "documentation, changelog and configuration walkthroughs."
        );
    }

    @Override
    public void execute(CommandLine cmd) {
        printStream.println("Usage:");
        printStream.println("  drasyl [flags]");
        printStream.println("  drasyl [command]");
        printStream.println();

        printStream.println("Available Commands:");
        for (Map.Entry<String, Command> entry : COMMANDS.entrySet()) {
            String name = entry.getKey();
            Command command = entry.getValue();
            printStream.print(String.format("%-15s", name));
            printStream.println(String.format("%-15s", command.getDescription()));
        }
        printStream.println();

        printStream.println("Flags:");
        HelpFormatter formatter = new HelpFormatter();
        PrintWriter printWriter = new PrintWriter(printStream);
        formatter.printOptions(printWriter, 100, super.getOptions(), 0, 6);
        printWriter.flush();
        printStream.println();

        printStream.println("Use \"drasyl [command] --help\" for more information about a command.");
    }

    @Override
    public String getDescription() {
        return "Show help for drasyl commands and flags.";
    }
}
