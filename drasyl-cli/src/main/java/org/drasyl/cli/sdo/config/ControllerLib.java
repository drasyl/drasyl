package org.drasyl.cli.sdo.config;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public class ControllerLib extends TwoArgFunction {
    public static LuaNetworkTable NETWORK;

    @Override
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        LuaValue library = tableOf();
        env.set("register_network", new RegisterNetwork());
        return library;
    }

    static class RegisterNetwork extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg) {
            final LuaTable networkTable = networkArg.checktable();

            if (NETWORK != null) {
                throw new LuaError("Only one network can be registered.");
            }

            NETWORK = (LuaNetworkTable) networkTable;

            return NIL;
        }
    }
}
