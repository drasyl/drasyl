package org.drasyl;

/**
 * An {@link Exception} which is thrown by {@link DrasylConfig}.
 */
public class DrasylConfigException extends IllegalArgumentException {
    public DrasylConfigException(final Throwable cause) {
        super(cause);
    }

    public DrasylConfigException(final String path, final Throwable cause) {
        super("Invalid value at '" + path + "'", cause);
    }

    public DrasylConfigException(final String path, final String message) {
        super("Invalid value at '" + path + "': " + message);
    }
}
