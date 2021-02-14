/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.cli.command;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.apache.commons.cli.CommandLine;
import org.drasyl.cli.CliException;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingSupplier;

import java.io.IOException;
import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Generate and output new Identity in JSON format.
 */
public class GenerateIdentityCommand extends AbstractCommand {
    private final ThrowingSupplier<Identity, IOException> identitySupplier;
    private final ThrowingBiConsumer<PrintStream, Identity, IOException> jsonWriter;

    GenerateIdentityCommand(final PrintStream out,
                            final PrintStream err,
                            final ThrowingSupplier<Identity, IOException> identitySupplier,
                            final ThrowingBiConsumer<PrintStream, Identity, IOException> jsonWriter) {
        super(out, err);
        this.identitySupplier = requireNonNull(identitySupplier);
        this.jsonWriter = requireNonNull(jsonWriter);
    }

    public GenerateIdentityCommand() {
        this(
                System.out, // NOSONAR
                System.err, // NOSONAR
                IdentityManager::generateIdentity,
                (myOut, identity) -> JACKSON_WRITER.with(new DefaultPrettyPrinter()).writeValue(myOut, identity)
        );
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate("genidentity", "Generate and output new Identity in JSON format.");
    }

    @Override
    protected void execute(final CommandLine cmd) {
        try {
            final Identity identity = identitySupplier.get();
            jsonWriter.accept(out, identity);
        }
        catch (final IOException e) {
            throw new CliException("Unable to output identity:", e);
        }
    }

    @Override
    public String getDescription() {
        return "Generate and output new Identity.";
    }
}
