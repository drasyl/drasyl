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
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
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
import java.util.Scanner;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WormholeCommandTest {
    private ByteArrayOutputStream outStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream out;
    private ByteArrayOutputStream errStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream err;
    @Mock
    private Supplier<Scanner> scannerSupplier;
    @Mock
    private ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    @Mock
    private ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;
    private WormholeCommand underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        errStream = new ByteArrayOutputStream();
        err = new PrintStream(errStream, true);
        underTest = new WormholeCommand(out, err, scannerSupplier, sendingNodeSupplier, receivingNodeSupplier);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Transfer a text message from one node to another, safely."));
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl wormhole [flags]" + System.lineSeparator()));
            assertThat(output, containsString("drasyl wormhole [command]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }

    @Nested
    class Execute {
        @Mock(answer = RETURNS_DEEP_STUBS)
        private CommandLine cmd;

        @Test
        void shouldRequestTextAndStartSendingNode(@Mock(answer = RETURNS_DEEP_STUBS) final SendingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("send");
            when(scannerSupplier.get()).thenReturn(new Scanner("Hallo Welt"));
            when(sendingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldUseGivenTextAndStartSendingNode(@Mock(answer = RETURNS_DEEP_STUBS) final SendingWormholeNode node) throws DrasylException, ParseException {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("send");
            when(cmd.hasOption("config")).thenReturn(false);
            when(cmd.hasOption("text")).thenReturn(true);
            when(cmd.getParsedOptionValue("text")).thenReturn("Hallo Welt");
            when(sendingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldRequestCodeAndStartReceivingNode(@Mock(answer = RETURNS_DEEP_STUBS) final ReceivingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("receive");
            when(scannerSupplier.get()).thenReturn(new Scanner(IdentityTestUtil.ID_1.getIdentityPublicKey().toString()));
            when(receivingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldUseGivenCodeAndStartReceivingNode(@Mock(answer = RETURNS_DEEP_STUBS) final ReceivingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(3);
            when(cmd.getArgList().get(1)).thenReturn("receive");
            when(cmd.getArgList().get(2)).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey().toString());
            when(receivingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldAbortOnInvalidCode(@Mock(answer = RETURNS_DEEP_STUBS) final ReceivingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(3);
            when(cmd.getArgList().get(1)).thenReturn("receive");
            when(cmd.getArgList().get(2)).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey().toString().substring(0, 63));
            when(receivingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node, never()).requestText(any(), any());

            final String output = errStream.toString();
            assertThat(output, containsString("ERR: Invalid wormhole code supplied: must be at least 64 characters long."));
        }

        @Test
        void shouldThrowExceptionOnUnknownCommand() {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("foo");

            underTest.execute(cmd);

            final String output = errStream.toString();
            assertThat(output, containsString("ERR: Unknown command \"foo\" for \"drasyl wormhole\"."));
        }

        @Test
        void shouldPrintHelpOnMissingCommand() {
            when(cmd.getArgList().size()).thenReturn(1);

            underTest.execute(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Transfer a text message from one node to another, safely."));
        }
    }
}
