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

import org.drasyl.util.Version;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static picocli.CommandLine.Command;

@Command(
        name = "version",
        header = "Shows the drasyl version number, the java version, and the architecture",
        synopsisHeading = "%nUsage: ",
        optionListHeading = "%n",
        showDefaultValues = true
)
public class VersionCommand implements Runnable {
    protected final PrintStream out;

    VersionCommand(final PrintStream out) {
        this.out = requireNonNull(out);
    }

    @SuppressWarnings("unused")
    public VersionCommand() {
        this(System.out); // NOSONAR
    }

    @Override
    public void run() {
        for (final Version version : Version.identify().values()) {
            out.println("- " + version.artifactId() + ".version " + version.version());
        }
        out.println("- java.version " + System.getProperty("java.version"));
        out.println("- os.name " + System.getProperty("os.name"));
        out.println("- os.version " + System.getProperty("os.version"));
        out.println("- os.arch " + System.getProperty("os.arch"));
    }
}
