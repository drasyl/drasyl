package org.drasyl.cli;

public class CommandNotFoundCliException extends CliException {
    public CommandNotFoundCliException(String commandName) {
        super("Unknown command \"" + commandName + "\" for \"drasyl\"");
    }
}
