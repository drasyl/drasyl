package org.drasyl.cli.sdo.config;

import org.drasyl.handler.peers.Peer;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class LuaPeerTable extends LuaTable {
    public LuaPeerTable(final Peer peer) {
        set("label", LuaValue.valueOf(peer.toString()));
        set("role", LuaValue.valueOf(peer.role().toString()));
        set("inetAddress", LuaValue.valueOf(peer.inetAddress().toString()));
        set("sent", LuaValue.valueOf(peer.sent()));
        set("last", LuaValue.valueOf(peer.last()));
        set("average", LuaValue.valueOf(peer.average()));
        set("best", LuaValue.valueOf(peer.best()));
        set("worst", LuaValue.valueOf(peer.worst()));
        set("st_dev", LuaValue.valueOf(peer.stDev()));
    }
}
