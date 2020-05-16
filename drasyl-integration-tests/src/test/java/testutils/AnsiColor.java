/*
 * Copyright (c) 2020.
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
package testutils;

public enum AnsiColor {
    COLOR_RESET("\u001B[0m"),
    COLOR_BLACK("\u001B[30m"),
    COLOR_RED("\u001B[31m"),
    COLOR_GREEN("\u001B[32m"),
    COLOR_YELLOW("\u001B[33m"),
    COLOR_BLUE("\u001B[34m"),
    COLOR_PURPLE("\u001B[35m"),
    COLOR_CYAN("\u001B[36m"),
    COLOR_WHITE("\u001B[37m"),
    STYLE_REVERSED("\u001b[7m"),
    STYLE_BOLD("\u001b[1m"),
    STYLE_UNDERLINE("\u001b[4m");
    private String color;

    AnsiColor(String color) {
        this.color = color;
    }

    public String getColor() {
        return this.color;
    }
}
