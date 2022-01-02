/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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

import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingBiFunction;
import picocli.CommandLine.Command;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

import static java.util.Objects.requireNonNull;
import static org.drasyl.identity.Identity.POW_DIFFICULTY;

/**
 * Generate and output a new proof of work for a given public key.
 */
@Command(
        name = "genpow",
        header = "Generates and outputs a new proof of work for a given public key",
        synopsisHeading = "%nUsage: ",
        optionListHeading = "%n",
        showDefaultValues = true,
        defaultValueProvider = GenerateProofOfWorkCommand.MyDefaultProvider.class
)
public class GenerateProofOfWorkCommand implements Runnable {
    private final Scanner in;
    private final PrintStream out;
    private final ThrowingBiFunction<IdentityPublicKey, Byte, ProofOfWork, IOException> proofOfWorkFunction;
    private final ThrowingBiConsumer<PrintStream, ProofOfWork, IOException> proofOfWorkWriter;
    @Option(
            names = { "--difficulty" },
            description = "Sets the difficulty of the proof of work."
    )
    private byte difficulty;

    GenerateProofOfWorkCommand(final Scanner in,
                               final PrintStream out,
                               final ThrowingBiFunction<IdentityPublicKey, Byte, ProofOfWork, IOException> proofOfWorkFunction,
                               final ThrowingBiConsumer<PrintStream, ProofOfWork, IOException> proofOfWorkWriter) {
        this.in = requireNonNull(in);
        this.out = requireNonNull(out);
        this.proofOfWorkFunction = requireNonNull(proofOfWorkFunction);
        this.proofOfWorkWriter = requireNonNull(proofOfWorkWriter);
    }

    @SuppressWarnings("unused")
    public GenerateProofOfWorkCommand() {
        this(
                new Scanner(System.in),
                System.out, // NOSONAR
                ProofOfWork::generateProofOfWork,
                PrintStream::println
        );
    }

    @Override
    public void run() {
        try {
            final IdentityPublicKey publicKey = IdentityPublicKey.of(in.nextLine());
            proofOfWorkWriter.accept(out, proofOfWorkFunction.apply(publicKey, difficulty));
            in.close();
        }
        catch (final IOException e) {
            throw new CliException("Unable to output proof of work:", e);
        }
    }

    static class MyDefaultProvider implements IDefaultValueProvider {
        public MyDefaultProvider() {
            // do not remove
        }

        @Override
        public String defaultValue(ArgSpec argSpec) throws Exception {
            return argSpec.isOption() ? optionDefaultValue((OptionSpec) argSpec) : null;
        }

        private String optionDefaultValue(OptionSpec optionSpec) {
            if ("--difficulty".equals(optionSpec.longestName())) {
                return Integer.toString(POW_DIFFICULTY);
            }
            else {
                return null;
            }
        }
    }
}
