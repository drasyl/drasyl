/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
import org.luaj.vm2.lib.LibFunction;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import static org.luaj.vm2.LuaValue.tableOf;

public class LuaHelper {
    private LuaHelper() {
        // util class
    }

    public static LuaTable createTable(final Collection<? extends LuaValue> collection) {
        final LuaTable table = tableOf();
        int index = 1;
        for (final LuaValue item : collection) {
            table.set(index++, item);
        }
        return table;
    }

    public static LuaTable createTable(final Map<String, Object> map) {
        final LuaTable table = tableOf();
        for (final Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String) {
                table.set(LuaString.valueOf(entry.getKey()), LuaString.valueOf((String) entry.getValue()));
            }
            else if (entry.getValue() instanceof Map) {
                table.set(LuaString.valueOf(entry.getKey()), createTable((Map<String, Object>) entry.getValue()));
            }

        }
        return table;
    }

    public static String toString(final LuaValue value) {
        if (value instanceof LuaTable) {
            final LuaTable table = (LuaTable) value;
            final StringBuilder result = new StringBuilder();
            result.append("{");
            for (final LuaValue key : table.keys()) {
                if (result.length() != 1) {
                    result.append(", ");
                }
                result.append(toString(key));
                result.append("=");
                result.append(toString(table.get(key)));
            }
            result.append("}");
            return result.toString();
        }
        else if (value instanceof LuaBoolean) {
            return value.tojstring();
        }
        else if (value instanceof LuaString) {
            return value.tojstring();
        }
        else if (value instanceof LuaInteger) {
            return value.tojstring();
        }
        else if (value instanceof LuaDouble) {
            return value.tojstring();
        }
        else if (value instanceof LibFunction) {
            return value.toString();
        }
        else {
            throw new RuntimeException("LuaStrings#toString not implemented for " + StringUtil.simpleClassName(value));
        }
    }

    public static int hash(final LuaValue value) {
        if (value instanceof LuaTable) {
            final LuaTable table = (LuaTable) value;
            int result = 1;
            for (final LuaValue key : table.keys()) {
                result = 31 * result + hash(key);
                result = 31 * result + hash(table.get(key));
            }
            return result;
        }
        else if (value instanceof LuaBoolean) {
            return Objects.hash(value.toboolean());
        }
        else if (value instanceof LuaString) {
            return Objects.hash(value.tojstring());
        }
        else if (value instanceof LuaInteger) {
            return Objects.hash(value.toint());
        }
        else if (value instanceof LuaDouble) {
            return Objects.hash(value.todouble());
        }
        else if (value instanceof LibFunction) {
            return 1;
        }
        else {
            throw new RuntimeException("LuaHashCodes#hash not implemented for " + StringUtil.simpleClassName(value));
        }
    }
}
