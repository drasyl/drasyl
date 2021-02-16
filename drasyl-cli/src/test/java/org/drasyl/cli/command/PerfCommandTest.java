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
import org.apache.commons.cli.ParseException;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.perf.PerfClientNode;
import org.drasyl.cli.command.perf.PerfServerNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ThrowingBiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfCommandTest {
    private ByteArrayOutputStream outStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream out;
    @SuppressWarnings("FieldCanBeLocal")
    private ByteArrayOutputStream errStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream err;
    @Mock
    private ThrowingBiFunction<DrasylConfig, PrintStream, PerfServerNode, DrasylException> serverNodeSupplier;
    @Mock
    private ThrowingBiFunction<DrasylConfig, PrintStream, PerfClientNode, DrasylException> clientNodeSupplier;
    @Mock
    private Consumer<Integer> exitSupplier;
    private PerfCommand underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        errStream = new ByteArrayOutputStream();
        err = new PrintStream(errStream, true);
        underTest = new PerfCommand(out, err, serverNodeSupplier, clientNodeSupplier, exitSupplier);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Tool for measuring network performance."));
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl perf [flags]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }

    @Nested
    class Execute {
        @Mock(answer = RETURNS_DEEP_STUBS)
        private CommandLine cmd;

        @Test
        void shouldStartServerNode(@Mock(answer = RETURNS_DEEP_STUBS) final PerfServerNode node) throws DrasylException {
            when(serverNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldStartClientNodeAndSetCorrectOptionsWhenClientOptionIsGiven(@Mock(answer = RETURNS_DEEP_STUBS) final PerfClientNode node) throws ParseException, DrasylException {
            when(cmd.hasOption("client")).thenReturn(true);
            when(cmd.getParsedOptionValue("client")).thenReturn("022e170caf9292de6af36562d2773e62d573e33d09550e1620b9cae75b1a3a98281ff73f2346d55195d0cd274c101c4775");
            when(clientNodeSupplier.apply(any(), any())).thenReturn(node);
            when(cmd.getParsedOptionValue("time")).thenReturn(20);
            when(cmd.getParsedOptionValue("mps")).thenReturn(200);
            when(cmd.getParsedOptionValue("size")).thenReturn(500);

            underTest.execute(cmd);

            verify(node).start();
            verify(node).setTestOptions(CompressedPublicKey.of("022e170caf9292de6af36562d2773e62d573e33d09550e1620b9cae75b1a3a98281ff73f2346d55195d0cd274c101c4775"), 20, 200, 500, false, false);
        }
    }
}
