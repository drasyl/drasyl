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

import org.drasyl.cli.converter.IdentityPublicKeyConverter;
import org.drasyl.cli.converter.InetSocketAddressConverter;
import org.drasyl.cli.perf.PerfCommand;
import org.drasyl.cli.tunnel.TunnelCommand;
import org.drasyl.cli.wormhole.WormholeCommand;
import org.drasyl.identity.IdentityPublicKey;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Provides a command line interface with drasyl-related tools (run root node, generate identity,
 * etc.).
 */
@Command(
        name = "drasyl",
        subcommands = {
                GenerateIdentityCommand.class,
                GenerateProofOfWorkCommand.class,
                HelpCommand.class,
                NodeCommand.class,
                PerfCommand.class,
                PublicKeyCommand.class,
                TunnelCommand.class,
                VersionCommand.class,
                WormholeCommand.class
        },
        headerHeading = "drasyl Command Line Interface: ",
        header = "A collection of utilities for drasyl.%n",
        commandListHeading = "%n",
        footerHeading = "%n",
        footer = "The environment variable JAVA_OPTS can be used to pass options to the JVM."
)
public class Cli {
    private final Function<Cli, CommandLine> commandLineSupplier;
    private final Consumer<Integer> exitSupplier;

    public Cli() {
        this(cli -> new CommandLine(cli), System::exit); // NOSONAR
    }

    Cli(final Function<Cli, CommandLine> commandLineSupplier,
        final Consumer<Integer> exitSupplier) {
        this.commandLineSupplier = requireNonNull(commandLineSupplier);
        this.exitSupplier = requireNonNull(exitSupplier);
    }

    public static void main(final String[] args) {
        new Cli().run(args);
    }

    public void run(final String[] args) {
        final CommandLine commandLine = commandLineSupplier.apply(this);
        commandLine.registerConverter(IdentityPublicKey.class, new IdentityPublicKeyConverter());
        commandLine.registerConverter(InetSocketAddress.class, new InetSocketAddressConverter());
        final int exitCode = commandLine.execute(args);
        exitSupplier.accept(exitCode);
    }
}
