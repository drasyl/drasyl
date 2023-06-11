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

public class LuaLinkTable extends LuaTable {
    private final LuaNetworkTable network;

    public LuaLinkTable(final LuaNetworkTable network,
                        final LuaString node1,
                        final LuaString node2,
                        final LuaTable params) {
        this.network = network;
        for (final LuaValue key : network.linkDefaults.keys()) {
            set(key, network.linkDefaults.get(key));
        }
        for (final LuaValue key : params.keys()) {
            set(key, params.get(key));
        }
        set("node1", node1);
        set("node2", node2);
    }

    public LuaLinkTable(final LuaNetworkTable network,
                        final LuaString node1,
                        final LuaString node2,
                        final LuaValue params) {
        this(network, node1, node2, params == NIL ? tableOf() : params.checktable());
    }

    public DrasylAddress node1() {
        return IdentityPublicKey.of(get("node1").tojstring());
    }

    public DrasylAddress node2() {
        return IdentityPublicKey.of(get("node2").tojstring());
    }

    public DrasylAddress other(final DrasylAddress name) {
        if (node1().equals(name)) {
            return node2();
        }
        else {
            return node1();
        }
    }

    @Override
    public int hashCode() {
        return LuaHashCodes.hash(this);
    }
}
