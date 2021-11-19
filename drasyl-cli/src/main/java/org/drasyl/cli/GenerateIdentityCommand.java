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

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.drasyl.identity.Identity;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingSupplier;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_WRITER;

/**
 * Generate and output new a identity in JSON format.
 */
@Command(
        name = "genidentity",
        header = "Generates and outputs a new identity",
        synopsisHeading = "%nUsage: "
)
public class GenerateIdentityCommand implements Runnable {
    protected final PrintStream out;
    private final ThrowingSupplier<Identity, IOException> identitySupplier;
    private final ThrowingBiConsumer<PrintStream, Identity, IOException> jsonWriter;

    GenerateIdentityCommand(final PrintStream out,
                            final ThrowingSupplier<Identity, IOException> identitySupplier,
                            final ThrowingBiConsumer<PrintStream, Identity, IOException> jsonWriter) {
        this.out = requireNonNull(out);
        this.identitySupplier = requireNonNull(identitySupplier);
        this.jsonWriter = requireNonNull(jsonWriter);
    }

    @SuppressWarnings("unused")
    public GenerateIdentityCommand() {
        this(
                System.out, // NOSONAR
                Identity::generateIdentity,
                (myOut, identity) -> JACKSON_WRITER.with(new DefaultPrettyPrinter()).writeValue(myOut, identity)
        );
    }

    @Override
    public void run() {
        try {
            final Identity identity = identitySupplier.get();
            jsonWriter.accept(out, identity);
        }
        catch (final IOException e) {
            throw new CliException("Unable to output identity:", e);
        }
    }
}
