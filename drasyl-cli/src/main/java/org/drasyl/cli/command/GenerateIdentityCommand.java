package org.drasyl.cli.command;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.apache.commons.cli.CommandLine;
import org.drasyl.cli.CliException;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;

import java.io.IOException;
import java.io.PrintStream;

import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Generate and output new Identity in JSON format.
 */
public class GenerateIdentityCommand extends AbstractCommand {
    public GenerateIdentityCommand() {
        this(System.out); // NOSONAR
    }

    GenerateIdentityCommand(PrintStream printStream) {
        super(printStream);
    }

    @Override
    protected void help(CommandLine cmd) {
        helpTemplate("genidentity", "Generate and output new Identity in JSON format.");
    }

    @Override
    protected void execute(CommandLine cmd) throws CliException {
        try {
            Identity identity = IdentityManager.generateIdentity();
            JACKSON_WRITER.with(new DefaultPrettyPrinter()).writeValue(printStream, identity);
        }
        catch (IdentityManagerException | IOException e) {
            throw new CliException(e);
        }
    }

    @Override
    public String getDescription() {
        return "Generate and output new Identity.";
    }
}
