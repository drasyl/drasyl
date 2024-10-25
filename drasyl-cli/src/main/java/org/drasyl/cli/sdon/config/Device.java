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
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Represents a device.
 */
public class Device extends LuaTable {
    Device(final DrasylAddress address, final String[] tags) {
        set("address", LuaValue.valueOf(address.toString()));
        set("online", FALSE);
        set("tags", LuaHelper.createTable(Arrays.asList(tags).stream().map(LuaValue::valueOf).collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        final LuaTable stringTable = tableOf();
        stringTable.set("address", get("address"));
        stringTable.set("online", get("online"));
        stringTable.set("tags", get("tags"));
        return "Device" + LuaStrings.toString(stringTable);
    }

    public void setOnline() {
        set("online", TRUE);
    }

    public void setOffline() {
        set("online", FALSE);
    }

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
