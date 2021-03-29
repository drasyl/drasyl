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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ExtendWith(MockitoExtension.class)
class VersionCommandTest {
    private ByteArrayOutputStream outStream;
    private PrintStream out;
    private ByteArrayOutputStream errStream;
    private PrintStream err;
    private VersionCommand underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        errStream = new ByteArrayOutputStream();
        err = new PrintStream(errStream, true);
        underTest = new VersionCommand(out, err);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Show the drasyl, os and java version number."));
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl version [flags]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }

    @Nested
    class Execute {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintVersion() {
            underTest.execute(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("drasyl v"));
        }
    }
}
