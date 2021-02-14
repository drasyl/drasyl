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
