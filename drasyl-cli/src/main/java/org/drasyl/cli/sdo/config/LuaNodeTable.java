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

import org.drasyl.cli.util.LuaHashCodes;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.HashSet;
import java.util.Set;

public class LuaNodeTable extends LuaTable {
    private final LuaNetworkTable network;

    public LuaNodeTable(final LuaNetworkTable network,
                        final LuaString name,
                        final LuaTable params) {
        this.network = network;
        for (final LuaValue key : network.nodeDefaults.keys()) {
            set(key, network.nodeDefaults.get(key));
        }
        for (final LuaValue key : params.keys()) {
            set(key, params.get(key));
        }
        set("name", name);
        set("state", new LuaNodeStateTable());
    }

    public LuaNodeTable(final LuaNetworkTable network,
                        final LuaString name,
                        final LuaValue params) {
        this(network, name, params == NIL ? tableOf() : params.checktable());
    }

    @Override
    public int hashCode() {
        return LuaHashCodes.hash(this);
    }

    public LuaNodeStateTable state() {
        return (LuaNodeStateTable) get("state");
    }

    public DrasylAddress name() {
        return IdentityPublicKey.of(get("name").tojstring());
    }

    public DrasylAddress defaultRoute() {
        final LuaValue defaultRoute = get("default_route");
        if (defaultRoute == NIL) {
            return null;
        }
        else {
            return IdentityPublicKey.of(defaultRoute.checkstring().tojstring());
        }
    }

    public Set<Policy> policies() {
        final Set<Policy> policies = new HashSet<>();

        final DrasylAddress defaultRoute = defaultRoute();
        if (defaultRoute != null) {
            policies.add(new DefaultRoutePolicy(defaultRoute));
        }

        // link policies
        final Set<LuaLinkTable> links = network.nodeLinks.get(name());
        for (final LuaLinkTable link : links) {
            policies.add(new LinkPolicy(link.other(name())));
        }

        return policies;
    }
}
