package org.drasyl.cli.util;

import io.netty.util.internal.StringUtil;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.LibFunction;

public class LuaStrings {
    private LuaStrings() {
        // util class
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
}
