/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.util;

import io.netty.util.internal.StringUtil;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.luaj.vm2.LuaValue.tableOf;

public class LuaClones {
    private LuaClones() {
        // util class
    }

    public static LuaValue clone(final LuaValue value) {
        if (value instanceof LuaTable) {
            final LuaTable table = (LuaTable) value;
            final LuaTable result = tableOf();
            for (final LuaValue key : table.keys()) {
                result.set(clone(key), clone(table.get(key)));
            }
            return result;
        }
        else if (value instanceof LuaBoolean) {
            return value;
        }
        else if (value instanceof LuaString) {
            return value;
        }
        else if (value instanceof LuaInteger) {
            return value;
        }
        else if (value instanceof LuaDouble) {
            return value;
        }
        else {
            throw new RuntimeException("LuaClones#clone not implemented for " + StringUtil.simpleClassName(value));
        }
    }
}
