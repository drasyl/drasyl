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

import org.drasyl.cli.util.LuaHashCodes;
import org.drasyl.cli.util.LuaStrings;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Represents a network node.
 */
public class NetworkNode extends LuaTable {
    private final Network network;

    NetworkNode(final Network network,
                final LuaString name,
                final LuaTable params) {
        this.network = requireNonNull(network);

        // name
        set("name", name);

        // ip
        LuaValue ip = params.get("ip");
        if (ip == NIL) {
            ip = LuaValue.valueOf(network.getNextIp());
        }
        set("ip", ip);
    }

    @Override
    public String toString() {
        final LuaTable stringTable = tableOf();
        stringTable.set("name", get("name"));
        stringTable.set("ip", get("ip"));
        return "Node" + LuaStrings.toString(stringTable);
    }

    @Override
    public int hashCode() {
        return LuaHashCodes.hash(this);
    }

    public DrasylAddress name() {
        return IdentityPublicKey.of(get("name").tojstring());
    }

    public int getDistance(final Device device) {
        // FIXME: implement
        return device.isOnline() ? 1 : Integer.MAX_VALUE;
    }

    public void setDevice(final Device device) {
        set("device", LuaString.valueOf(device.address().toString()));
    }

    public DrasylAddress device() {
        if (get("device") != NIL) {
            return IdentityPublicKey.of(get("device").tojstring());
        }
        return null;
    }

    public Set<Policy> createPolicies() {
        try {
            final Set<Policy> policies = new HashSet<>();

            final Set<NetworkLink> links = network.nodeLinks.get(get("name"));
            final Map<LuaString, NetworkNode> nodes = network.getNodes();

            // TunPolicy
            final String ipString = get("ip").tojstring();
            final String[] parts = ipString.split("/", 2);
            final InetAddress ipAddress = InetAddress.getByName(parts[0]);
            final short ipNetmask = Short.valueOf(parts[1]);
            final Map<InetAddress, DrasylAddress> mapping = new HashMap<>();
            for (final NetworkLink link : links) {
                final LuaString peerName = link.other(get("name").checkstring());
                final NetworkNode peer = nodes.get(peerName);
                final DrasylAddress peerAddress = peer.device();
                if (peerAddress != null) {
                    final InetAddress peerIpAddress = InetAddress.getByName(peer.get("ip").tojstring().split("/", 2)[0]);
                    mapping.put(peerIpAddress, peerAddress);
                }
            }

            final Policy tunPolicy = new TunPolicy(ipAddress, ipNetmask, mapping);
            policies.add(tunPolicy);

            return policies;
        }
        catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
