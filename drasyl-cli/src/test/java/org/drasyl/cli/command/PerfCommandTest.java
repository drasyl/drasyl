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
import org.apache.commons.cli.ParseException;
import org.drasyl.cli.command.perf.PerfClientNode;
import org.drasyl.cli.command.perf.PerfServerNode;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.util.ThrowingBiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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
    private PerfCommand underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        errStream = new ByteArrayOutputStream();
        err = new PrintStream(errStream, true);
        underTest = new PerfCommand(out, err, serverNodeSupplier, clientNodeSupplier);
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
            when(cmd.getParsedOptionValue("client")).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey().toString());
            when(clientNodeSupplier.apply(any(), any())).thenReturn(node);
            when(cmd.getParsedOptionValue("time")).thenReturn(20);
            when(cmd.getParsedOptionValue("mps")).thenReturn(200);
            when(cmd.getParsedOptionValue("size")).thenReturn(500);

            underTest.execute(cmd);

            verify(node).start();
            verify(node).setTestOptions(IdentityTestUtil.ID_1.getIdentityPublicKey(), 20, 200, 500, false, false);
        }
    }
}
