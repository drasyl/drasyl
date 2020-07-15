package org.drasyl.cli.command;

import ch.qos.logback.classic.Level;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.event.Event;
import org.drasyl.util.DrasylFunction;
import org.drasyl.util.DrasylScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintStream;

@SuppressWarnings("common-java:DuplicatedBlocks")
public class NodeCommand extends AbstractCommand {
    private static final Logger log = LoggerFactory.getLogger(NodeCommand.class);
    private static final String DEFAULT_CONF = "drasyl.conf";
    private static final String OPT_VERBOSE = "verbose";
    private static final String OPT_CONFIG = "config";
    private final DrasylFunction<DrasylConfig, DrasylNode, DrasylException> nodeSupplier;
    private DrasylNode node;

    public NodeCommand() {
        this(
                System.out, // NOSONAR
                config -> new DrasylNode(config) {
                    @Override
                    public void onEvent(Event event) {
                        log.info("Event received: {}", event);
                    }
                },
                null
        );
    }

    NodeCommand(PrintStream printStream,
                DrasylFunction<DrasylConfig, DrasylNode, DrasylException> nodeSupplier,
                DrasylNode node) {
        super(printStream);
        this.nodeSupplier = nodeSupplier;
        this.node = node;
    }

    @SuppressWarnings({ "java:S1192" })
    @Override
    protected void help(CommandLine cmd) {
        helpTemplate(
                "node",
                "Run a drasyl node in the current directory.",
                DEFAULT_CONF + " syntax:\n" +
                        "drasyl {\n" +
                        "  identity {\n" +
                        "    public-key = \"...\"\n" +
                        "    private-key = \"...\"\n" +
                        "  }\n" +
                        "\n" +
                        "  server {\n" +
                        "    enabled = true\n" +
                        "    bind-host = \"0.0.0.0\"\n" +
                        "    bind-port = 22527\n" +
                        "  }\n" +
                        "\n" +
                        "  super-peer {\n" +
                        "    enabled = true\n" +
                        "    endpoints = [\"wss://staging.env.drasyl.org\"]\n" +
                        "    public-key = \"\"\n" +
                        "  }\n" +
                        "}\n" +
                        "\n" +
                        "Use \"drasyl node --config my-" + DEFAULT_CONF + "\" to use a custom config."
        );
    }

    @Override
    public void execute(CommandLine cmd) throws CliException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (node != null) {
                log.info("Shutdown Drasyl Node");
                node.shutdown();
            }
        }));

        try {
            DrasylConfig config;
            config = getDrasylConfig(cmd);

            node = nodeSupplier.apply(config);
            node.start();
            node.shutdownFuture().join();
            DrasylScheduler.shutdown();
        }
        catch (DrasylException e) {
            throw new CliException(e);
        }
    }

    private DrasylConfig getDrasylConfig(CommandLine cmd) {
        DrasylConfig config;
        if (!cmd.hasOption(OPT_CONFIG)) {
            File defaultFile = new File(DEFAULT_CONF);
            if (defaultFile.exists()) {
                log.info("Node is using default configuration file '{}'", defaultFile);
                config = DrasylConfig.parseFile(defaultFile);
            }
            else {
                log.info("Node is using configuration defaults as '{}' does not exist", DEFAULT_CONF);
                config = new DrasylConfig();
            }
        }
        else {
            File file = new File(cmd.getOptionValue(OPT_CONFIG));
            log.info("Node is using configuration file '{}'", file);
            config = DrasylConfig.parseFile(file);
        }

        // override log level
        if (cmd.hasOption(OPT_VERBOSE)) {
            String level = cmd.getOptionValue(OPT_VERBOSE);
            config = DrasylConfig.newBuilder(config).loglevel(Level.valueOf(level)).build();
        }
        return config;
    }

    @Override
    protected Options getOptions() {
        Options options = super.getOptions();

        Option loglevel = Option.builder("v").longOpt(OPT_VERBOSE).hasArg().argName("level").desc("Sets the log level (off, error, warn, info, debug, trace; default: warn)").build();
        options.addOption(loglevel);

        Option configfile = Option.builder("c").longOpt(OPT_CONFIG).hasArg().argName("file").desc("Load configuration from specified file.").build();
        options.addOption(configfile);

        return options;
    }

    @Override
    public String getDescription() {
        return "Run a drasyl node.";
    }
}
