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
package org.drasyl.cli.sdon.config;

import org.drasyl.cli.util.LuaHelper;
import org.drasyl.cli.util.LuaStrings;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Device extends LuaTable {
    private static final Logger LOG = LoggerFactory.getLogger(Device.class);

    public Device(final DrasylAddress address, final String[] tags) {
        set("address", LuaValue.valueOf(address.toString()));
        set("online", FALSE);
        set("tags", LuaHelper.createTable(Arrays.asList(tags).stream().map(LuaValue::valueOf).collect(Collectors.toList())));
//        set("policies", tableOf());
//        set("peers", tableOf());
//        set("store", tableOf());
    }

    @Override
    public String toString() {
        final LuaTable publicTable = tableOf();
        publicTable.set("address", get("address"));
        publicTable.set("online", get("online"));
        publicTable.set("tags", get("tags"));
        return "Device" + LuaStrings.toString(publicTable);
    }

    public void setOnline() {
        set("online", TRUE);
    }

    public void setOffline() {
        set("online", FALSE);
    }

//    public void setState(final Set<Policy> policies,
//                         final Map<DrasylAddress, Peer> peers,
//                         final Map<String, Object> store) {
//        // policies
//        final LuaTable policiesTable = tableOf();
//        int index = 1;
//        for (final Policy policy : policies) {
//            policiesTable.set(index++, new LuaPolicyTable(policy));
//        }
//        set("policies", policiesTable);
//
//        // peers
//        final LuaTable peersTable = tableOf();
//        for (final Entry<DrasylAddress, Peer> entry : peers.entrySet()) {
//            peersTable.set(LuaValue.valueOf(entry.getKey().toString()), new LuaPeerTable(entry.getValue()));
//        }
//        set("peers", peersTable);
//
//        // store
//        final LuaTable storeTable = tableOf();
//        for (final Entry<String, Object> entry : store.entrySet()) {
//            if (entry.getKey().equals("computation")) {
//                final LuaTable computationTable = tableOf();
//                final List<Map<String, String>> results = (List<Map<String, String>>) entry.getValue();
//                for (final Map<String, String> result : results) {
//                    final LuaTable computationTableEntry = tableOf();
//                    for (final Map.Entry<String, String> resultEntry : result.entrySet()) {
//                        computationTableEntry.set(resultEntry.getKey(), resultEntry.getValue());
//                    }
//
////                    LOG.error("COMPUTATION PUT {}={}", computationTable.keyCount(), computationTableEntry);
//                    computationTable.insert(computationTable.keyCount(), computationTableEntry);
//                }
//                storeTable.set("computation", computationTable);
//            }
//        }
//        set("store", storeTable);
//    }

    public boolean isOnline() {
        return get("online") == TRUE;
    }

    public boolean isOffline() {
        return get("online") == FALSE;
    }

    public DrasylAddress address() {
        return IdentityPublicKey.of(get("address").tojstring());
    }
}
