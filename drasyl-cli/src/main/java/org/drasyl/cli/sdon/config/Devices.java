/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.identity.DrasylAddress;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Devices extends LuaTable {
    Devices() {
    }

    @Override
    public String toString() {
        return "Devices" + LuaHelper.toString(this);
    }

    public Device getOrCreateDevice(final DrasylAddress address) {
        final LuaValue device = get(address.toString());
        if (device != NIL) {
            return (Device) device;
        }
        else {
            final Device newDevice = new Device(address);
            set(address.toString(), newDevice);
            return newDevice;
        }
    }

    public Collection<Device> getDevices() {
        final Set<Device> devices = new HashSet<>();
        final LuaValue[] keys = keys();
        for (final LuaValue key : keys) {
            devices.add((Device) get(key));
        }
        return devices;
    }
}
