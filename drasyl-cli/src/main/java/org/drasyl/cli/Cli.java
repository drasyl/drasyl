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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.cli.*;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Cli {
    private static final Logger log = LoggerFactory.getLogger(Cli.class);
    private static final String CONF = "drasyl.conf";
    private static final String LOGLEVEL = "info";
    private static final String OPT_VERSION = "version";
    private static final String OPT_LOGLEVEL = "loglevel";
    private static final String OPT_CONFIGFILE = "configfile";
    private static final String OPT_HELP = "help";
    private DrasylNode node;

    public Cli() {
    }

    public static void main(String[] args) throws CliException {
        Cli cli = new Cli();
        cli.run(args);
        Runtime.getRuntime().addShutdownHook(new Thread(cli::shutdown));
    }

    public void run(String[] args) throws CliException {
        Options options = getOptions();

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(OPT_LOGLEVEL)) {
                setLogLevel(cmd.getOptionValue(OPT_LOGLEVEL));
            }
            else {
                setLogLevel(LOGLEVEL);
            }

            if (cmd.hasOption(OPT_HELP)) {
                printHelp(options);
            }
            else if (cmd.hasOption(OPT_VERSION)) {
                printVersion();
            }
            else {
                try {
                    runNode(cmd);
                }
                catch (CliShutdownException e) {
                    // do nothing
                }
            }
        }
        catch (ParseException e) {
            throw new CliException(e);
        }
    }

    void shutdown() {
        if (node != null) {
            log.info("Shutdown Drasyl Node");
            node.shutdown().join();
            node.irrevocablyTerminate();
        }
    }

    private Options getOptions() {
        Options options = new Options();

        Option version = Option.builder("v").longOpt(OPT_VERSION).desc("display version").build();
        options.addOption(version);

        Option loglevel = Option.builder("l").longOpt(OPT_LOGLEVEL).hasArg().argName("level").desc("sets the log level (off, error, warn, info, debug, trace; default: " + LOGLEVEL + ")").build();
        options.addOption(loglevel);

        Option configfile = Option.builder("f").longOpt(OPT_CONFIGFILE).hasArg().argName("file").desc("load configuration from specified file").build();
        options.addOption(configfile);

        Option help = Option.builder("h").longOpt(OPT_HELP).desc("show this file").build();
        options.addOption(help);

        return options;
    }

    private void setLogLevel(String value) {
        Level level = Level.valueOf(value);

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLoggerList().stream().filter(l -> l.getName().startsWith("org.drasyl")).forEach(l -> l.setLevel(level));
    }

    private void printHelp(Options options) {
        String header = "" +
                "       drasyl\n" +
                "       drasyl -f ~/drasyl.conf\n" +
                "\n" +
                "Run a Drasyl Node in the current directory.\n" +
                "If the file '" + CONF + "' exists, that configuration is applied.\n" +
                "\n" +
                "Options:";

        // TODO: Add configuration example
        String footer = "\n" +
                CONF + " syntax:\n" +
                "drasyl {\n" +
                "  ...TODO...\n" +
                "}";

        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        formatter.setSyntaxPrefix("Usage: ");
        formatter.printHelp("drasyl [options]", header, options, footer);
    }

    private void printVersion() {
        String version = DrasylNode.getVersion();
        System.out.println(version);
    }

    private void runNode(CommandLine cmd) throws CliException {
        try {
            node = new DrasylNode() {
                @Override
                public void onEvent(Event event) {
                    log.info("Event received: {}", event);
                }
            };
            node.start().join();
        }
        catch (DrasylException e) {
            throw new CliException(e);
        }
    }
}
