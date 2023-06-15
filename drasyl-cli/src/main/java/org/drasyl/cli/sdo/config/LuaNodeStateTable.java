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

import org.drasyl.handler.peers.Peer;
import org.drasyl.handler.peers.PeersList;
import org.drasyl.identity.DrasylAddress;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class LuaNodeStateTable extends LuaTable {

    public LuaNodeStateTable() {
        set("online", LuaValue.FALSE);
        set("policies", tableOf());
        set("peers", tableOf());
    }

    public void setOnline() {
        set("online", TRUE);
    }

    public void setOffline() {
        set("online", LuaValue.FALSE);
    }

    public void setState(final Set<Policy> policies, final Map<DrasylAddress, Peer> peers) {
        // policies
        final LuaTable policiesTable = tableOf();
        int index = 1;
        for (final Policy policy : policies) {
            policiesTable.set(index++, new LuaPolicyTable(policy));
        }
        set("policies", policiesTable);

        // peers
        final LuaTable peersTable = tableOf();
        for (final Map.Entry<DrasylAddress, Peer> entry : peers.entrySet()) {
            peersTable.set(LuaValue.valueOf(entry.getKey().toString()), new LuaPeerTable(entry.getValue()));
        }
        set("peers", peersTable);
    }

    public boolean isOnline() {
        return get("online") == TRUE;
    }

    public boolean isOffline() {
        return get("online") == FALSE;
    }
}
