package org.drasyl.cli.sdon.config;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/**
 * Lua API provided by the controller.
 */
public class ControllerLib extends TwoArgFunction {
    public static NetworkTable NETWORK;

    @Override
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        final LuaValue library = tableOf();
        env.set("create_network", new CreateNetworkFunction());
        env.set("register_network", new RegisterNetworkFunction());
        env.set("inspect", new InspectFunction());
        return library;
    }

    static class CreateNetworkFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue paramsArg) {
            return new NetworkTable(paramsArg);
        }
    }

    static class RegisterNetworkFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg) {
            final LuaTable networkTable = networkArg.checktable();

            if (NETWORK != null) {
                throw new LuaError("Only one network can be registered.");
            }

            NETWORK = (NetworkTable) networkTable;

            return NIL;
        }
    }

    static class InspectFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue arg) {
            return LuaValue.valueOf(arg.toString());
        }
    }
}
