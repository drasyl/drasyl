package org.drasyl.cli.command;

import ch.qos.logback.classic.Level;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

abstract class AbstractCommand implements Command {
    static final Path DEFAULT_CONF_PATH = Paths.get("drasyl.conf").toAbsolutePath();
    private static final String OPT_HELP = "help";
    private static final String OPT_VERBOSE = "verbose";
    private static final String OPT_CONFIG = "config";
    private static final Logger log = LoggerFactory.getLogger(AbstractCommand.class);
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

            DrasylNode.setLogLevel(getLoglevel(cmd));
            log.debug("drasyl: Version '{}' starting with parameters [{}]", DrasylNode.getVersion(), args.length > 0 ? "'" + String.join("', '", args) + "'" : "");

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

    @SuppressWarnings({ "java:S1192" })
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
                printStream.printf("%-15s", command);
                printStream.printf("%-15s%n", description);
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

        Option verbose = Option.builder("v").longOpt(OPT_VERBOSE).hasArg().argName("level").desc("Sets the log level (off, error, warn, info, debug, trace; default: warn)").build();
        options.addOption(verbose);

        Option config = Option.builder("c").longOpt(OPT_CONFIG).hasArg().argName("file").desc("Load configuration from specified file (default: " + DEFAULT_CONF_PATH + ").").build();
        options.addOption(config);

        return options;
    }

    protected abstract void help(CommandLine cmd) throws CliException;

    protected abstract void execute(CommandLine cmd) throws CliException;

    protected Level getLoglevel(CommandLine cmd) {
        Level level;
        if (cmd.hasOption(OPT_VERBOSE)) {
            String levelString = cmd.getOptionValue(OPT_VERBOSE);
            level = Level.valueOf(levelString);
        }
        else {
            level = DrasylNode.getLogLevel();
        }

        return level;
    }

    protected DrasylConfig getDrasylConfig(CommandLine cmd) {
        DrasylConfig config;
        if (cmd.hasOption(OPT_CONFIG)) {
            File file = new File(cmd.getOptionValue(OPT_CONFIG));
            log.info("Using config file from '{}'", file);
            config = DrasylConfig.parseFile(file);
        }
        else if (DEFAULT_CONF_PATH.toFile().exists()) {
            log.info("Using config file from '{}'", DEFAULT_CONF_PATH);
            config = DrasylConfig.parseFile(DEFAULT_CONF_PATH.toFile());
        }
        else {
            log.info("Config file '{}' not found - using defaults", DEFAULT_CONF_PATH);
            config = new DrasylConfig();
        }

        // override loglevel
        Level loglevel = getLoglevel(cmd);
        if (config.getLoglevel() != loglevel) {
            config = DrasylConfig.newBuilder(config).loglevel(loglevel).build();
        }

        return config;
    }
}