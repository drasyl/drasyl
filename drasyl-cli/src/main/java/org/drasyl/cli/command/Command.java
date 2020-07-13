package org.drasyl.cli.command;

import org.drasyl.cli.CliException;

public interface Command {
    void execute(String[] args) throws CliException;
    String getDescription();
}
