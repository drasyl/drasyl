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

import org.drasyl.cli.util.LuaHashCodes;
import org.drasyl.cli.util.LuaStrings;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class NodeTable extends LuaTable {
    private static final Logger LOG = LoggerFactory.getLogger(NodeTable.class);
    private final NetworkTable network;

    public NodeTable(final NetworkTable network,
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

//        for (final LuaValue key : network.nodeDefaults.keys()) {
//            final LuaValue defaultValue = network.nodeDefaults.get(key);
//            set(key, LuaClones.clone(defaultValue));
//        }
//        for (final LuaValue key : params.keys()) {
//            set(key, params.get(key));
//        }

        //set("state", new LuaNodeStateTable());
        //set("links", new LinksValue());
    }

    @Override
    public String toString() {
        final LuaTable publicTable = tableOf();
        publicTable.set("name", get("name"));
        publicTable.set("ip", get("ip"));
        return "Node" + LuaStrings.toString(publicTable);
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
        return IdentityPublicKey.of(get("device").tojstring());
    }

    public Set<Policy> createPolicies(final NetworkTable network) {
        try {
            final Set<Policy> policies = new HashSet<>();

            // IpPolicy
            final String ipString = get("ip").tojstring();
            final String[] parts = ipString.split("/", 2);
            final InetAddress ipAddress = InetAddress.getByName(parts[0]);
            final short ipNetmask = Short.valueOf(parts[1]);
            final Policy ipPolicy = new IpPolicy(ipAddress, ipNetmask);
            policies.add(ipPolicy);

            // LinkPolicies
            final Set<LinkTable> links = network.nodeLinks.get(get("name"));
            final Map<LuaString, NodeTable> nodes = network.getNodes();
            for (final LinkTable link : links) {
                final LuaString peerName = link.other(get("name").checkstring());
                Device peerDevice = null;
                for (final Map.Entry<Device, LuaString> entry : assignments.entrySet()) {
                    if (entry.getValue().equals(peerName)) {
                        peerDevice = entry.getKey();
                        break;
                    }
                }
                if (peerDevice != null) {
                    final NodeTable peer = nodes.get(peerName);
                    final InetAddress peerIpAddress = InetAddress.getByName(peer.get("ip").tojstring().split("/", 2)[0]);
                    final Policy linkPolicy = new LinkPolicy(peerName, peerDevice.address(), peerIpAddress);
                    policies.add(linkPolicy);
                }
            }

            return policies;
        }
        catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
