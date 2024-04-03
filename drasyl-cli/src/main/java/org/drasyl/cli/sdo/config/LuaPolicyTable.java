/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.cli.sdo.config;

import org.drasyl.identity.DrasylAddress;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import static java.util.Objects.requireNonNull;

public class LuaPolicyTable extends LuaTable {
    private final Policy policy;

    public LuaPolicyTable(final Policy policy) {
        this.policy = requireNonNull(policy);
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
        set("tostring", new ToStringFunction());
    }

    class ToStringFunction extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            return LuaValue.valueOf(LuaPolicyTable.this.policy.toString());
        }
    }
}
