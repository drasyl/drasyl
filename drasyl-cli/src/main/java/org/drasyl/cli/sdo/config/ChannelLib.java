package org.drasyl.cli.sdo.config;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class ChannelLib extends TwoArgFunction {
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        final LuaValue library = tableOf();
        library.set("directPath", NIL);
        library.set("tun_route", LuaValue.valueOf(false));
        library.set("extend", new UtilLib.extend());
        env.set("Channel", library);
        return library;
    }
}
