package org.drasyl.cli.sdo.config;

import org.drasyl.identity.DrasylAddress;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class LuaPolicyTable extends LuaTable {
    public LuaPolicyTable(final Policy policy) {
        set("label", LuaValue.valueOf(policy.toString()));
        set("current_state", LuaValue.valueOf(policy.currentState().toString()));
        set("desired_state", LuaValue.valueOf(policy.desiredState().toString()));
        if (policy instanceof LinkPolicy) {
            set("type", "link");
            set("peer", LuaValue.valueOf(((LinkPolicy) policy).peer().toString()));
        }
        else if (policy instanceof DefaultRoutePolicy) {
            set("type", "default_route");
            set("default_route", LuaValue.valueOf(((DefaultRoutePolicy) policy).defaultRoute().toString()));
        }
        else if (policy instanceof TunPolicy) {
            set("type", "tun");
            set("name", LuaValue.valueOf(((TunPolicy) policy).name()));
            set("subnet", LuaValue.valueOf(((TunPolicy) policy).subnet()));
            set("mtu", LuaValue.valueOf(((TunPolicy) policy).mtu()));
            set("address", LuaValue.valueOf(((TunPolicy) policy).address().toString()));
            final DrasylAddress defaultRoute = ((TunPolicy) policy).defaultRoute();
            set("default_route", defaultRoute != null ? LuaValue.valueOf(defaultRoute.toString()) : NIL);
        }
    }
}
