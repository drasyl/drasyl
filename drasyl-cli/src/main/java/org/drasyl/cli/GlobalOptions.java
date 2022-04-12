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
package org.drasyl.cli;

import ch.qos.logback.classic.Level;
import org.drasyl.cli.converter.LevelConverter;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.logging.Slf4JLogger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        synopsisHeading = "%nUsage: ",
        optionListHeading = "%n",
        showDefaultValues = true
)
@SuppressWarnings("java:S118")
public abstract class GlobalOptions {
    @SuppressWarnings("unused")
    @Option(
            names = { "-v", "--verbose" },
            description = "Sets the log level (available values: off, error, warn, info, debug, trace).",
            paramLabel = "<level>",
            converter = LevelConverter.class,
            defaultValue = "warn"
    )
    protected Level logLevel;

    protected GlobalOptions(final Level logLevel) {
        this.logLevel = logLevel;
    }

    @SuppressWarnings("unused")
    protected GlobalOptions() {

    }

    /**
     * Sets the level defined in {@link #logLevel} for all Logger within the package {@code
     * org.drasyl}.
     */
    @SuppressWarnings("java:S1312")
    protected void setLogLevel() {
        final Logger logger = LoggerFactory.getLogger("org.drasyl");
        if (logger instanceof Slf4JLogger && ((Slf4JLogger) logger).delegate() instanceof ch.qos.logback.classic.Logger) {
            ((ch.qos.logback.classic.Logger) ((Slf4JLogger) logger).delegate()).setLevel(logLevel);
        }
    }

    protected abstract Logger log();
}
