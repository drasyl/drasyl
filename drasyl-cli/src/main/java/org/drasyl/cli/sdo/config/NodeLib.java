package org.drasyl.cli.sdo.config;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class NodeLib extends TwoArgFunction {
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        final LuaValue library = tableOf();
        library.set("tun_enabled", LuaValue.valueOf(false));
        library.set("tun_name", LuaValue.valueOf("utun0"));
        library.set("tun_subnet", LuaValue.valueOf("10.10.2.0/24"));
        library.set("tun_mtu", LuaValue.valueOf(1225));
        library.set("extend", new UtilLib.extend());
        env.set("Node", library);
        return library;
    }
}
