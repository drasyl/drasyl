package org.drasyl.cli.sdon.config;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

@SuppressWarnings("java:S110")
public class NetworkLib extends TwoArgFunction {
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        LuaValue library = tableOf();
        env.set("Network", new NetworkConstructor());
        return library;
    }

    public static class NetworkConstructor extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue paramsArg) {
            return new LuaNetworkTable(paramsArg);
        }
    }
}
