package org.drasyl.cli;

/**
 * A org.drasyl.cli.CliException is thrown by the {@link Cli} when errors occur.
 */
class CliException extends Exception {
    public CliException(String message) {
        super(message);
    }

    public CliException(Throwable cause) {
        super(cause);
    }
}