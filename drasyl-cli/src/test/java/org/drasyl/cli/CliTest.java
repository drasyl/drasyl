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

import org.drasyl.cli.command.Command;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CliTest {
    @Mock
    private PrintStream err;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Map<String, Command> commands;
    @Mock
    private Command command;
    @Mock
    private Consumer<Integer> exitSupplier;
    @InjectMocks
    private Cli underTest;

    @Nested
    class Run {
        @Test
        void shouldExecuteHelpCommandIfNothingIsGiven() {
            when(commands.get("help")).thenReturn(command);

            underTest.run(new String[]{});

            verify(commands.get("help")).execute(new String[]{});
            verify(exitSupplier).accept(0);
        }

        @Test
        void shouldExecuteHelpCommandIfNothingButHelpParameterIsGiven() {
            when(commands.get("help")).thenReturn(command);

            underTest.run(new String[]{ "--help" });

            verify(commands.get("help")).execute(new String[]{});
            verify(exitSupplier).accept(0);
        }

        @Test
        void shouldExecuteHelpCommandIfNothingButHParameterIsGiven() {
            when(commands.get("help")).thenReturn(command);

            underTest.run(new String[]{ "-h" });

            verify(commands.get("help")).execute(new String[]{});
            verify(exitSupplier).accept(0);
        }

        @Test
        void shouldExecuteGivenCommand() {
            when(commands.get("version")).thenReturn(command);

            underTest.run(new String[]{ "version" });

            verify(commands.get("version")).execute(new String[]{ "version" });
            verify(exitSupplier).accept(0);
        }

        @Test
        void shouldExitWithErrorForUnknownCommand() {
            underTest.run(new String[]{ "sadassdaashdaskj" });

            verify(exitSupplier).accept(1);
        }
    }
}
