package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylNode;

import java.io.PrintStream;

public class VersionCommand extends AbstractCommand {
    public VersionCommand() {
        this(System.out); // NOSONAR
    }

    VersionCommand(PrintStream printStream) {
        super(printStream);
    }

    @Override
    protected void help(CommandLine cmd) {
        helpTemplate("version", "Show the drasyl, os and java version number.");
    }

    @Override
    public void execute(CommandLine cmd) {
        printStream.println("drasyl v" + DrasylNode.getVersion());
        printStream.println("- os.name " + System.getProperty("os.name"));
        printStream.println("- os.version " + System.getProperty("os.version"));
        printStream.println("- os.arch " + System.getProperty("os.arch"));
        printStream.println("- java.version " + System.getProperty("java.version"));
    }

    @Override
    public String getDescription() {
        return "Show the version number.";
    }
}
