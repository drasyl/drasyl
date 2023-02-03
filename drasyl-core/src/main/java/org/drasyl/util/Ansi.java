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

import org.drasyl.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.List;

import static org.drasyl.util.Ansi.Color.BLACK;
import static org.drasyl.util.Ansi.Color.BLUE;
import static org.drasyl.util.Ansi.Color.CYAN;
import static org.drasyl.util.Ansi.Color.GREEN;
import static org.drasyl.util.Ansi.Color.MAGENTA;
import static org.drasyl.util.Ansi.Color.RED;
import static org.drasyl.util.Ansi.Color.WHITE;
import static org.drasyl.util.Ansi.Color.YELLOW;

/**
 * Utility to colorize and style Strings with ANSI escape sequences.
 * <p>
 * Usage:
 * <pre><code>
 * String msg1 = ansi().red().onBlue().swap().format("Hello %s", name);
 * String msg2 = ansi().color(MAGENTA).background(GREEN).format("Hello World");
 * </code></pre>
 */
@UnstableApi
public final class Ansi {
    private static final int COLOR_BASE = 30;
    private static final int BACKGROUND_BASE = 40;
    private static final String RESET = "\u001B[0m";
    // styles
    private static final String BOLD = "\u001b[1m";
    private static final String ITALIC = "\u001b[3m";
    private static final String UNDERLINE = "\u001b[4m";
    private static final String SWAP = "\u001b[7m";
    private final List<String> codes;

    private Ansi(final List<String> codes) {
        this.codes = List.copyOf(codes);
    }

    private Ansi code(final String style) {
        final List<String> newCodes = new ArrayList<>(codes);
        newCodes.add(style);
        return new Ansi(newCodes);
    }

    @SuppressWarnings("java:S1845")
    public Ansi reset() {
        return code(RESET);
    }

    // colors
    public Ansi color(final Color color) {
        return code("\u001B[" + (COLOR_BASE + color.code) + "m");
    }

    public Ansi black() {
        return color(BLACK);
    }

    public Ansi red() {
        return color(RED);
    }

    public Ansi green() {
        return color(GREEN);
    }

    public Ansi yellow() {
        return color(YELLOW);
    }

    public Ansi blue() {
        return color(BLUE);
    }

    public Ansi purple() {
        return color(MAGENTA);
    }

    public Ansi cyan() {
        return color(CYAN);
    }

    public Ansi white() {
        return color(WHITE);
    }

    // background colors
    public Ansi background(final Color color) {
        return code("\u001B[" + (BACKGROUND_BASE + color.code) + "m");
    }

    public Ansi onBlack() {
        return background(BLACK);
    }

    public Ansi onRed() {
        return background(RED);
    }

    public Ansi onGreen() {
        return background(GREEN);
    }

    public Ansi onYellow() {
        return background(YELLOW);
    }

    public Ansi onBlue() {
        return background(BLUE);
    }

    public Ansi onMagenta() {
        return background(MAGENTA);
    }

    public Ansi onCyan() {
        return background(CYAN);
    }

    public Ansi onWhite() {
        return background(WHITE);
    }

    // styles
    public Ansi bold() {
        return code(BOLD);
    }

    public Ansi italic() {
        return code(ITALIC);
    }

    public Ansi underline() {
        return code(UNDERLINE);
    }

    public Ansi swap() {
        return code(SWAP);
    }

    public String format(final String format) {
        return String.join("", codes) + format + RESET;
    }

    /**
     * @throws java.util.IllegalFormatException If a format string contains an illegal syntax, a
     *                                          format specifier that is incompatible with the given
     *                                          arguments, insufficient arguments given the format
     *                                          string, or other illegal conditions.
     */
    public String format(final String format, final Object... args) {
        return String.join("", codes) + String.format(format, args) + RESET;
    }

    public static Ansi ansi() {
        return new Ansi(List.of());
    }

    public enum Color {
        BLACK((short) 0),
        RED((short) 1),
        GREEN((short) 2),
        YELLOW((short) 3),
        BLUE((short) 4),
        MAGENTA((short) 5),
        CYAN((short) 6),
        WHITE((short) 7);
        private final short code;

        Color(final short code) {
            this.code = code;
        }
    }
}
