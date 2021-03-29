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
import org.drasyl.cli.CliException;
import org.drasyl.identity.Identity;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateIdentityCommandTest {
    private ByteArrayOutputStream outStream;
    @SuppressWarnings({ "FieldCanBeLocal" })
    private PrintStream out;
    @SuppressWarnings({ "FieldCanBeLocal", "unused" })
    private ByteArrayOutputStream errStream;
    @SuppressWarnings({ "FieldCanBeLocal" })
    private PrintStream err;
    @Mock
    private ThrowingSupplier<Identity, IOException> identitySupplier;
    @Mock
    private ThrowingBiConsumer<PrintStream, Identity, IOException> jsonWriter;
    private GenerateIdentityCommand underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        errStream = new ByteArrayOutputStream();
        err = new PrintStream(outStream, true);
        underTest = new GenerateIdentityCommand(out, err, identitySupplier, jsonWriter);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Generate and output new Identity in JSON format."));
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl genidentity [flags]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }

    @Nested
    class Execute {
        @Test
        void shouldGenerateIdentity() throws IOException {
            underTest.execute(new String[]{});

            verify(identitySupplier).get();
            verify(jsonWriter).accept(any(), any());
        }

        @Test
        void shouldThrowExceptionIfIdentityCouldNotBeGenerated() throws IOException {
            when(identitySupplier.get()).thenThrow(IOException.class);

            assertThrows(CliException.class, () -> underTest.execute(new String[]{}));
        }

        @Test
        void shouldThrowExceptionIfIdentityCouldNotBePrinted() throws IOException {
            doThrow(IOException.class).when(jsonWriter).accept(any(), any());

            assertThrows(CliException.class, () -> underTest.execute(new String[]{}));
        }
    }

    @Nested
    class GetDescription {
        @Test
        void shouldReturnString() {
            assertNotNull(underTest.getDescription());
        }
    }
}
