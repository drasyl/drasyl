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
