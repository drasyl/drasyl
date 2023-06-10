package org.drasyl.cli.sdo.config;

import org.drasyl.cli.sdo.config.NodeLib;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class UtilLib extends TwoArgFunction {
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        env.set("extend", new extend());
        env.set("node", new NodeLib());
        return env;
    }

    static class extend extends TwoArgFunction {
        public LuaValue call(final LuaValue arg1, final LuaValue arg2) {
            // extend arg1 by arg2
            final LuaTable table1 = arg1.checktable();
            final LuaTable table2 = arg2.checktable();
            for (LuaValue key : table2.keys()) {
                table1.set(key, table2.get(key));
            }
            return table1;
        }
    }
}
