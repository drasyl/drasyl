package org.drasyl.cli;

/**
 * Is thrown by the {@link Cli} if desired command is not found.
 */
public class CommandNotFoundCliException extends CliException {
    public CommandNotFoundCliException(String commandName) {
        super("Unknown command \"" + commandName + "\" for \"drasyl\"");
    }
}
