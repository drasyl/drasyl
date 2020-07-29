package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.drasyl.cli.CliException;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;

abstract class AbstractCommand implements Command {
    private static final String OPT_HELP = "help";
    protected final PrintStream printStream;

    public AbstractCommand(
            PrintStream printStream) {
        this.printStream = printStream;
    }

    @Override
    public void execute(String[] args) throws CliException {
        Options flags = getOptions();
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(flags, args);
            if (cmd.hasOption(OPT_HELP)) {
                help(cmd);
            }
            else {
                execute(cmd);
            }
        }
        catch (ParseException e) {
            throw new CliException(e);
        }
    }

    protected void helpTemplate(String name, String header, String footer) {
        helpTemplate(name, header, footer, Map.of());
    }

    protected void helpTemplate(String name,
                                String header,
                                String footer,
                                Map<String, String> commands) {
        if (!header.isEmpty()) {
            printStream.println(header);
            printStream.println();
        }

        printStream.println("Usage:");
        printStream.println("  drasyl" + (!name.isEmpty() ? " " + name : "") + " [flags]");
        if (!commands.isEmpty()) {
            printStream.println("  drasyl" + (!name.isEmpty() ? " " + name : "") + " [command]");
        }
        printStream.println();

        if (!commands.isEmpty()) {
            printStream.println("Available Commands:");
            for (Map.Entry<String, String> entry : commands.entrySet()) {
                String command = entry.getKey();
                String description = entry.getValue();
                printStream.print(String.format("%-15s", command));
                printStream.println(String.format("%-15s", description));
            }
            printStream.println();
        }

        printStream.println("Flags:");
        HelpFormatter formatter = new HelpFormatter();
        PrintWriter printWriter = new PrintWriter(printStream);
        formatter.printOptions(printWriter, 100, getOptions(), 0, 6);
        printWriter.flush();

        if (!footer.isEmpty()) {
            printStream.println();
            printStream.println(footer);
        }
    }

    protected void helpTemplate(String name, String header) {
        helpTemplate(name, header, "");
    }

    protected Options getOptions() {
        Options options = new Options();

        // global flags
        Option help = Option.builder("h").longOpt(OPT_HELP).desc("Show this help.").build();
        options.addOption(help);

        return options;
    }

    protected abstract void help(CommandLine cmd) throws CliException;

    protected abstract void execute(CommandLine cmd) throws CliException;
}
