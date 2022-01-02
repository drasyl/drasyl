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

import org.drasyl.identity.IdentitySecretKey;
import picocli.CommandLine.Command;

import java.io.PrintStream;
import java.util.Scanner;

import static java.util.Objects.requireNonNull;

/**
 * Generate and output new a identity in JSON format.
 */
@Command(
        name = "pubkey",
        header = "Dervices the public key and prints it to standard output from a private key given on standard input",
        synopsisHeading = "%nUsage: ",
        optionListHeading = "%n",
        showDefaultValues = true
)
public class PublicKeyCommand implements Runnable {
    private final Scanner in;
    private final PrintStream out;

    PublicKeyCommand(final Scanner in, final PrintStream out) {
        this.in = requireNonNull(in);
        this.out = requireNonNull(out);
    }

    @SuppressWarnings("unused")
    public PublicKeyCommand() {
        this(new Scanner(System.in), System.out); // NOSONAR
    }

    @Override
    public void run() {
        final IdentitySecretKey secretKey = IdentitySecretKey.of(in.nextLine());
        out.println(secretKey.derivePublicKey());
        in.close();
    }
}
