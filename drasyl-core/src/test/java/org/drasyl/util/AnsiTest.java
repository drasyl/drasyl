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
package org.drasyl.util;

import org.junit.jupiter.api.Test;

import static org.drasyl.util.Ansi.ansi;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnsiTest {
    @Test
    void shouldApplyGivenAnsiCodesToString() {
        assertEquals("\u001B[30m\u001B[47m\u001B[7mHello, World!\u001B[0m", ansi().black().onWhite().swap().format("Hello, %s!", "World"));
        assertEquals("\u001B[31m\u001B[44mHello World\u001B[0m", ansi().red().onBlue().format("Hello World"));
        assertEquals("\u001B[32m\u001B[40m\u001B[1mHello World\u001B[0m", ansi().green().onBlack().bold().format("Hello World"));
        assertEquals("\u001B[33m\u001B[3m\u001B[4m\u001B[41mHello World\u001B[0m", ansi().yellow().italic().underline().onRed().format("Hello World"));
        assertEquals("\u001B[34m\u001B[35m\u001B[42mHello World\u001B[0m", ansi().blue().purple().onGreen().format("Hello World"));
        assertEquals("\u001B[37m\u001B[43mHello World\u001B[0m", ansi().white().onYellow().format("Hello World"));
        assertEquals("\u001B[36mHello World\u001B[0m", ansi().cyan().format("Hello World"));
        assertEquals("\u001B[44mHello World\u001B[0m", ansi().onBlue().format("Hello World"));
        assertEquals("\u001B[45mHello World\u001B[0m", ansi().onMagenta().format("Hello World"));
        assertEquals("\u001B[46m\u001B[47mHello World\u001B[0m", ansi().onCyan().onWhite().format("Hello World"));
        assertEquals("\u001B[46m\u001B[47m\u001B[0mHello World\u001B[0m", ansi().onCyan().onWhite().reset().format("Hello World"));
    }
}
