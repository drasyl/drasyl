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

import org.drasyl.identity.Identity;
import org.drasyl.node.IdentityFile;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingSupplier;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.PrintStream;

import static java.util.Objects.requireNonNull;

/**
 * Generate and output a new identity in JSON format.
 */
@Command(
        name = "genidentity",
        header = "Generates and outputs a new identity",
        synopsisHeading = "%nUsage: ",
        optionListHeading = "%n",
        showDefaultValues = true
)
public class GenerateIdentityCommand implements Runnable {
    private final PrintStream out;
    private final ThrowingSupplier<Identity, IOException> identitySupplier;
    private final ThrowingBiConsumer<PrintStream, Identity, IOException> identityWriter;

    GenerateIdentityCommand(final PrintStream out,
                            final ThrowingSupplier<Identity, IOException> identitySupplier,
                            final ThrowingBiConsumer<PrintStream, Identity, IOException> identityWriter) {
        this.out = requireNonNull(out);
        this.identitySupplier = requireNonNull(identitySupplier);
        this.identityWriter = requireNonNull(identityWriter);
    }

    @SuppressWarnings("unused")
    public GenerateIdentityCommand() {
        this(
                System.out, // NOSONAR
                Identity::generateIdentity,
                (myOut, identity) -> IdentityFile.writeTo(myOut, identity)
        );
    }

    @Override
    public void run() {
        try {
            final Identity identity = identitySupplier.get();
            identityWriter.accept(out, identity);
        }
        catch (final IOException e) {
            throw new CliException("Unable to output identity:", e);
        }
    }
}
