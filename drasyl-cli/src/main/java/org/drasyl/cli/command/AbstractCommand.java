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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.logging.Slf4JLogger;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

abstract class AbstractCommand implements Command {
    private static final String OPT_HELP = "help";
    private static final String OPT_VERBOSE = "verbose";
    private static final String OPT_CONFIG = "config";
    private static final int LINE_WIDTH = 100;
    private static final int DESCRIPTION_PADDING = 6;
    protected final PrintStream out;
    protected final PrintStream err;

    protected AbstractCommand(
            final PrintStream out,
            final PrintStream err) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
    }

    @Override
    public void execute(final String[] args) {
        final Options flags = getOptions();
        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(flags, args);

            setLogLevel(cmd);
            log().debug("drasyl: Version `{}` starting with parameters [{}]", DrasylNode::getVersion, () -> args.length > 0 ? ("'" + String.join("', '", args) + "'") : "");

            if (cmd.hasOption(OPT_HELP)) {
                help(cmd);
            }
            else {
                execute(cmd);
            }
        }
        catch (final ParseException e) {
            throw new CliException(e);
        }
    }

    protected abstract Logger log();

    @SuppressWarnings("SameParameterValue")
    protected void helpTemplate(final String name, final String header, final String footer) {
        helpTemplate(name, header, footer, Map.of());
    }

    @SuppressWarnings({ "java:S1192" })
    protected void helpTemplate(final String name,
                                final String header,
                                final String footer,
                                final Map<String, String> commands) {
        if (!header.isEmpty()) {
            out.println(header);
            out.println();
        }

        out.println("Usage:");
        out.println("  drasyl" + (!name.isEmpty() ? (" " + name) : "") + " [flags]");
        if (!commands.isEmpty()) {
            out.println("  drasyl" + (!name.isEmpty() ? (" " + name) : "") + " [command]");
        }
        out.println();

        if (!commands.isEmpty()) {
            out.println("Available Commands:");
            for (final Map.Entry<String, String> entry : commands.entrySet()) {
                final String command = entry.getKey();
                final String description = entry.getValue();
                out.printf("%-15s", command);
                out.printf("%-15s%n", description);
            }
            out.println();
        }

        out.println("Flags:");
        final HelpFormatter formatter = new HelpFormatter();
        final PrintWriter printWriter = new PrintWriter(out, false, UTF_8);
        formatter.printOptions(printWriter, LINE_WIDTH, getOptions(), 0, DESCRIPTION_PADDING);
        printWriter.flush();

        if (!footer.isEmpty()) {
            out.println();
            out.println(footer);
        }
    }

    protected void helpTemplate(final String name, final String header) {
        helpTemplate(name, header, "");
    }

    protected Options getOptions() {
        final Options options = new Options();

        // global flags
        final Option help = Option.builder("h").longOpt(OPT_HELP).desc("Show this help.").build();
        options.addOption(help);

        final Option verbose = Option.builder("v").longOpt(OPT_VERBOSE).hasArg().argName("level").desc("Sets the log level (off, error, warn, info, debug, trace; default: warn)").build();
        options.addOption(verbose);

        final Option config = Option.builder("c").longOpt(OPT_CONFIG).hasArg().argName("file").desc("Load configuration from specified file (default: " + getDefaultConfPath() + ").").build();
        options.addOption(config);

        return options;
    }

    @SuppressWarnings({ "unused" })
    protected abstract void help(CommandLine cmd);

    protected abstract void execute(CommandLine cmd);

    @SuppressWarnings("java:S1312")
    protected void setLogLevel(final CommandLine cmd) {
        if (cmd.hasOption(OPT_VERBOSE)) {
            final String levelString = cmd.getOptionValue(OPT_VERBOSE);

            final Logger logger = LoggerFactory.getLogger("org.drasyl");
            if (logger instanceof Slf4JLogger && ((Slf4JLogger) logger).delegate() instanceof ch.qos.logback.classic.Logger) {
                final Level level = Level.valueOf(levelString);
                ((ch.qos.logback.classic.Logger) ((Slf4JLogger) logger).delegate()).setLevel(level);
            }
        }
    }

    protected DrasylConfig getDrasylConfig(final CommandLine cmd) {
        final DrasylConfig config;
        if (cmd.hasOption(OPT_CONFIG)) {
            final File file = new File(cmd.getOptionValue(OPT_CONFIG));
            log().info("Using config file from `{}`", file);
            config = DrasylConfig.parseFile(file);
        }
        else {
            final Path defaultConfPath = getDefaultConfPath();
            if (defaultConfPath.toFile().exists()) {
                log().info("Using config file from `{}`", defaultConfPath);
                config = DrasylConfig.parseFile(defaultConfPath.toFile());
            }
            else {
                log().info("Config file `{}` not found - using defaults", defaultConfPath);
                config = DrasylConfig.of();
            }
        }

        return config;
    }

    static Path getDefaultConfPath() {
        // do not replace with constant. otherwise native image will always return build environment's path
        return Paths.get("drasyl.conf").toAbsolutePath();
    }
}
