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
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
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

    public Device state() {
        return (Device) get("state");
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

    public boolean isTunEnabled() {
        return get("tun_enabled") == LuaBoolean.TRUE;
    }

    public String tunName() {
        return get("tun_name").checkstring().tojstring();
    }

    public String tunSubnet() {
        return get("tun_subnet").checkstring().tojstring();
    }

    public int tunMtu() {
        return get("tun_mtu").checkint();
    }

    public InetAddress tunAddress() {
        if (get("tun_address") != NIL) {
            try {
                return InetAddress.getByName(get("tun_address").checkstring().tojstring());
            } catch (final UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            return null;
        }
    }

    public Map<InetAddress, DrasylAddress> tunRoutes() {
        try {
            final Map<InetAddress, DrasylAddress> routes = new HashMap<>();
            final LuaTable luaTable = get("tun_routes").checktable();
            for (final LuaValue key : luaTable.keys()) {
                final LuaValue value = luaTable.get(key);
                routes.put(InetAddress.getByName(key.checkstring().tojstring()), IdentityPublicKey.of(value.checkstring().tojstring()));
            }
            return routes;
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<Policy> policies() {
        final Set<Policy> policies = new HashSet<>();

//        // proactive latency measurements
//        //LOG.error("HEIKO network.proactiveLatencyMeasurementsRatio = {}", network.proactiveLatencyMeasurementsRatio);
//        if (network.proactiveLatencyMeasurementsRatio != null && network.proactiveLatencyMeasurementsRatio.tofloat() > 0 && network.proactiveLatencyMeasurementsInterval.toint() > 0) {
//            final float ratio = network.proactiveLatencyMeasurementsRatio.tofloat();
//            final int interval = network.proactiveLatencyMeasurementsInterval.toint();
//
//            final long currentTimeMillis = System.currentTimeMillis();
//            final long roundedTimeMillis = currentTimeMillis - (currentTimeMillis % interval);
//
//            final List<DrasylAddress> nodeCandidates = new ArrayList<>();
//            if (network.proactiveLatencyMeasurementsCandidates != null) {
//                final LuaValue[] keys = network.proactiveLatencyMeasurementsCandidates.keys();
//                for (final LuaValue key : keys) {
//                    final LuaValue value = network.proactiveLatencyMeasurementsCandidates.get(key);
//                    //System.out.println("value = " + value.tojstring());
//                    final IdentityPublicKey peer = IdentityPublicKey.of(value.tojstring());
//                    nodeCandidates.add(peer);
//                }
//            }
//            else {
//                // get online nodes
//                network.nodes.forEach((address, node) -> {
//                    if (!name().equals(address) && node.state().isOnline()) {
//                        nodeCandidates.add(address);
//                    }
//                });
//            }
//
//            final Set<DrasylAddress> randomSubset;
//            if (!electedPeers.containsKey(name())) {
//                // select ratio %/100 nodes
//                final Random random = new Random(name().hashCode() + roundedTimeMillis);
//                final int n = (int) (nodeCandidates.size() * ratio);
//                //LOG.error("HEIKO ratio = {}; n = {}; nodeCandidates.size() = {}", ratio, n, nodeCandidates.size());
//                randomSubset = new HashSet<>();
//                while (randomSubset.size() < n) {
//                    if (chosenPeers.size() >= nodeCandidates.size()) {
//                        chosenPeers.clear();
//                    }
//                    final List<DrasylAddress> currentCandidates = new ArrayList<>(SetUtil.difference(new HashSet<>(nodeCandidates), chosenPeers));
//
//                    final int randomIndex = random.nextInt(currentCandidates.size());
//                    randomSubset.add(currentCandidates.get(randomIndex));
//                    chosenPeers.add(currentCandidates.get(randomIndex));
//                }
//
//                electedPeers.put(name(), randomSubset);
//            }
//            else {
//                randomSubset = electedPeers.get(name());
//            }
//
//            //LOG.error("node {} elected {}", name(), randomSubset);
//
//            policies.add(new ProactiveLatencyMeasurementsPolicy(randomSubset));
//        }
//
//        // default route
//        final DrasylAddress defaultRoute = defaultRoute();
//        if (defaultRoute != null) {
//            policies.add(new DefaultRoutePolicy(defaultRoute));
//        }
//
//        final Set<LinkTable> links = network.nodeLinks.get(name());
//        if (isTunEnabled()) {
//            // tun
//            final Map<InetAddress, DrasylAddress> routes = tunRoutes();
////            LOG.error("{} tunRoutes() = {}", name(), tunRoutes());
//            for (final LinkTable link : links) {
//                final DrasylAddress otherName = link.other(name());
//                final NodeTable other = network.getNode(otherName);
//                if (other.tunAddress() != null) {
//                    routes.putIfAbsent(other.tunAddress(), otherName);
//                }
//            }
////            if (RELAYS.contains(name()) || name().equals(CLIENT)) {
////                LOG.error("{} links = {}", name(), links);
////                LOG.error("{} routes = {}", name(), routes);
////            }
//
//            policies.add(new TunPolicy(tunName(), tunSubnet(), tunMtu(), tunAddress(), routes, defaultRoute()));
//        }
//
//        // link policies
//        for (final LinkTable link : links) {
//            policies.add(new LinkPolicy(link.other(name())));
//        }

        //policies.add(new ComputationResultMessageParserPolicy());

        return policies;
    }

    class LinksValue extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            final LuaTable linksTable = tableOf();
//            int index = 1;
//            final SetMultimap<DrasylAddress, LinkTable> nodeLinks = NodeTable.this.network.nodeLinks;
//            for (final LinkTable link : nodeLinks.get(NodeTable.this.name())) {
//                linksTable.set(index++, link);
//            }
            return linksTable;
        }
    }
}
