package org.drasyl.cli.command;

import org.drasyl.cli.CliException;

/**
 * Defines a command of the {@link org.drasyl.cli.Cli}.
 */
public interface Command {
    void execute(String[] args) throws CliException;
    String getDescription();
}