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
package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.util.Version;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

/**
 * Show the version number.
 */
public class VersionCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(VersionCommand.class);

    public VersionCommand() {
        this(System.out, System.err); // NOSONAR
    }

    VersionCommand(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate("version", "Show the drasyl, os and java version number.");
    }

    @Override
    public void execute(final CommandLine cmd) {
        for (Version version : Version.identify().values()) {
            out.println("- " + version.artifactId() + ".version " + version.version());
        }
        out.println("- os.name " + System.getProperty("os.name"));
        out.println("- os.version " + System.getProperty("os.version"));
        out.println("- os.arch " + System.getProperty("os.arch"));
        out.println("- java.version " + System.getProperty("java.version"));
    }

    @Override
    public String getDescription() {
        return "Show the version number.";
    }
}
