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

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.sdo.message.ControllerHello;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.CsvWriter;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;

@SuppressWarnings("java:S110")
public class LuaNetworkTable extends LuaTable {
    static final DrasylAddress CLIENT;
    static final Set<DrasylAddress> RELAYS;

    static {
        CLIENT = IdentityPublicKey.of("fe8793d5e8f8a2dcdeada2065b9162596f947619723fe101b8db186b49d01b9b");
        RELAYS = Set.of(
            IdentityPublicKey.of("9269fff2b347ab343b6ca09dae8ec49f11b6d26c3116c3dfec907d252dd1ea6d"),
            IdentityPublicKey.of("841d1fafd67fc753a1c7c7e1350a0ef1567367d41131526e295951f54f27c35d"),
            IdentityPublicKey.of("daa21ed33a5092d7f8cd138b8ee5ef4765256efeb3b2ad644e1eebac7223e409"),
            IdentityPublicKey.of("582f79b32211a5068d52a5ca8707ce73690c359b29448a270e09e3571fe1241f"),
            IdentityPublicKey.of("7d54a2ec282c4674dd3c4da66e8529f8a248a542a7b6ed1f6e65927de037815a"),
            IdentityPublicKey.of("512c9c4b91ca6157ddb6af7d2d3e0f414ed40cef67528518cf242ce3ae955286"),
            IdentityPublicKey.of("0eed51a3c3df18a25281f1c69b187b8f1e9c63386f65599fe6d598e463f38f77"),
            IdentityPublicKey.of("7f7519e67bf24261e6e485918dd564a5541ba25ebdff3f1013f36e67401f5070")
        );
    }

    private static final Logger LOG = LoggerFactory.getLogger(LuaNetworkTable.class);
    final LuaTable nodeDefaults = new LuaTable();
    final LuaTable linkDefaults = new LuaTable();
    public final Map<DrasylAddress, LuaNodeTable> nodes = new HashMap<>();
    final Set<LuaLinkTable> links = new HashSet<>();
    final SetMultimap<DrasylAddress, LuaLinkTable> nodeLinks = new HashSetMultimap<>();
    public LuaClosure networkListener;
    LuaNumber proactiveLatencyMeasurementsRatio;
    LuaNumber proactiveLatencyMeasurementsInterval;
    LuaTable proactiveLatencyMeasurementsCandidates;
    final CsvWriter writer;
    public final Map<DrasylAddress, Set<Policy>> nodePolicies = new HashMap<>();

    {
        try {
            writer = new CsvWriter(new File("results/" + System.getenv("EXPERIMENT_NAME") + "_relays.csv"), "time", "relay", "count");
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public LuaNetworkTable(final LuaTable params) {
        nodeDefaults.set("default_route", NIL);

        nodeDefaults.set("tun_enabled", LuaValue.valueOf(false));
        nodeDefaults.set("tun_name", LuaValue.valueOf("utun0"));
        nodeDefaults.set("tun_subnet", LuaValue.valueOf("10.10.2.0/24"));
        nodeDefaults.set("tun_mtu", LuaValue.valueOf(1225));
        nodeDefaults.set("tun_routes", tableOf());
        // FIXME: add tun_address default

        for (final LuaValue key : params.keys()) {
            switch (key.checkstring().tojstring()) {
                case "node_defaults":
                    final LuaTable nodeDefaults = params.get(key).checktable();
                    for (final LuaValue key2 : nodeDefaults.keys()) {
                        this.nodeDefaults.set(key2, nodeDefaults.get(key2));
                    }
                    break;
                case "link_defaults":
                    final LuaTable linkDefaults = params.get(key).checktable();
                    for (final LuaValue key2 : linkDefaults.keys()) {
                        this.linkDefaults.set(key2, linkDefaults.get(key2));
                    }
                    break;
                case "network_listener":
                    this.networkListener = (LuaClosure) params.get(key).checkfunction();
                    break;
                case "proactive_latency_measurements_ratio":
                    this.proactiveLatencyMeasurementsRatio = params.get(key).checknumber();
                    break;
                case "proactive_latency_measurements_interval":
                    this.proactiveLatencyMeasurementsInterval = params.get(key).checknumber();
                    break;
                case "proactive_latency_measurements_candidates":
                    this.proactiveLatencyMeasurementsCandidates = params.get(key).checktable();
                    break;
                default:
                    throw new LuaError("Param `" + key.checkstring().tojstring() + "` does not exist.");
            }
        }

        // nodes
        set("tostring", new ToStringFunction());
        set("nodes", new NodesValue());
        set("get", new GetNodeFunction());
        set("add_node", new AddNodeFunction());
        set("remove_node", new RemoveNodeFunction());
        set("clear_nodes", new ClearNodesFunction());

        // link
        set("links", new LinksValue());
        set("add_link", new AddLinkFunction());
        set("remove_link", new RemoveLinkFunction());
        set("clear_links", new ClearLinksFunction());
    }

    public LuaNetworkTable(final LuaValue params) {
        this(params == NIL ? tableOf() : params.checktable());
    }

    public LuaNodeTable getNode(final DrasylAddress name) {
        return nodes.get(name);
    }

    @Override
    public String toString() {
        return "LuaNetworkTable{" +
                "networkListener=" + (networkListener != null ? "[SET]" : "[UNSET]") +
                ", proactiveLatencyMeasurementsRatio=" + proactiveLatencyMeasurementsRatio +
                ", proactiveLatencyMeasurementsInterval=" + proactiveLatencyMeasurementsInterval +
                ", proactiveLatencyMeasurementsCandidates=" + proactiveLatencyMeasurementsCandidates +
                ", nodes=" + nodes +
                ", links=" + links +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final LuaNetworkTable that = (LuaNetworkTable) o;
        return Objects.equals(nodes, that.nodes) && Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, links);
    }

    public boolean notifyListener(final ChannelHandlerContext ctx) throws IOException {
        if (networkListener != null) {
            final int before = hashCode();
            networkListener.call(this);
            final int after = hashCode();
            final boolean networkChanged = before != after;

            if (networkChanged) {
                LOG.trace("Network has changed. Push new policies to all online nodes.");

                // push new config to all nodes
                final Map<SocketAddress, DrasylChannel> channels = ((DrasylServerChannel) ctx.channel()).channels;
                for (final LuaNodeTable node : nodes.values()) {
                    final DrasylChannel channel = channels.get(node.name());
                    if (node.state().isOnline()) {
                        final Set<Policy> policies = node.policies();

                        final ControllerHello controllerHello = new ControllerHello(policies);
                        LOG.error("Send {} to {}.", controllerHello, node.name());
                        channel.writeAndFlush(controllerHello).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    }
                }
            }

            logRelays();

            return networkChanged;
        }

        return false;
    }

    public void logRelays() throws IOException {
        final Map<DrasylAddress, Integer> relayCounters = new HashMap<>();
        for (final LuaNodeTable node : nodes.values()) {
            if (CLIENT.equals(node.name()) && node.state().isOnline()) {
                //LOG.error("Node {} has {} tun routes.", node.name(), node.tunRoutes().size());
                for (final Entry<InetAddress, DrasylAddress> entry : node.tunRoutes().entrySet()) {
                    relayCounters.putIfAbsent(entry.getValue(), 0);
                    relayCounters.put(entry.getValue(), relayCounters.get(entry.getValue()) + 1);
                }
            }
        }

        final LocalDateTime now = LocalDateTime.now();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
        final String format = now.format(formatter);
        for (Map.Entry<DrasylAddress, Integer> entry : relayCounters.entrySet()) {
            writer.write(format, entry.getKey(), entry.getValue());
        }
    }

    class ToStringFunction extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            return LuaValue.valueOf(LuaNetworkTable.this.toString());
        }
    }

    class NodesValue extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            final LuaTable nodesTable = tableOf();
            int index = 1;
            for (final LuaNodeTable node : LuaNetworkTable.this.nodes.values()) {
                nodesTable.set(index++, node);
            }
            return nodesTable;
        }
    }

    class GetNodeFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue nameArg) {
            final LuaString nameString = nameArg.checkstring();
            final LuaNodeTable node = LuaNetworkTable.this.nodes.get(IdentityPublicKey.of(nameString.tojstring()));
            if (node != null) {
                return node;
            }
            else {
                return NIL;
            }
        }
    }

    class AddNodeFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue nameArg, final LuaValue paramsArg) {
            final LuaString nameString = nameArg.checkstring();

            final LuaNodeTable node = new LuaNodeTable(LuaNetworkTable.this, nameString, paramsArg);
            LuaNetworkTable.this.nodes.put(node.name(), node);

            return NIL;
        }
    }

    class RemoveNodeFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue nameArg) {
            final String nameString = nameArg.checkstring().tojstring();
            final boolean removed = LuaNetworkTable.this.nodes.remove(IdentityPublicKey.of(nameString)) != null;
            return LuaValue.valueOf(removed);
        }
    }

    class ClearNodesFunction extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            LuaNetworkTable.this.nodes.clear();
            return NIL;
        }
    }

    class AddLinkFunction extends ThreeArgFunction {
        @Override
        public LuaValue call(final LuaValue node1Arg,
                             final LuaValue node2Arg,
                             final LuaValue paramsArg) {
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();

            if (!LuaNetworkTable.this.nodes.containsKey(IdentityPublicKey.of(node1String.tojstring()))) {
                throw new LuaError("Node `" + node1String + "` does not exist.");
            }
            if (!LuaNetworkTable.this.nodes.containsKey(IdentityPublicKey.of(node2String.tojstring()))) {
                throw new LuaError("Node `" + node2String + "` does not exist.");
            }

            final LuaLinkTable link = new LuaLinkTable(LuaNetworkTable.this, node1String, node2String, paramsArg);
            //LOG.error("add_link: link = {}", link);
            final boolean newLink = LuaNetworkTable.this.links.add(link);
            LuaNetworkTable.this.nodeLinks.put(link.node1(), link);
            LuaNetworkTable.this.nodeLinks.put(link.node2(), link);

            return newLink ? TRUE : FALSE;
        }
    }

    class RemoveLinkFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue node1Arg,
                             final LuaValue node2Arg) {
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();

            if (!LuaNetworkTable.this.nodes.containsKey(IdentityPublicKey.of(node1String.tojstring()))) {
                throw new LuaError("Node `" + node1String + "` does not exist.");
            }
            if (!LuaNetworkTable.this.nodes.containsKey(IdentityPublicKey.of(node2String.tojstring()))) {
                throw new LuaError("Node `" + node2String + "` does not exist.");
            }

            final Iterator<LuaLinkTable> iterator = LuaNetworkTable.this.links.iterator();

            while (iterator.hasNext()) {
                final LuaLinkTable link = iterator.next();

                if (
                        (link.node1().equals(IdentityPublicKey.of(node1String.tojstring())) && link.node2().equals(IdentityPublicKey.of(node1String.tojstring()))) ||
                        (link.node1().equals(IdentityPublicKey.of(node2String.tojstring())) && link.node2().equals(IdentityPublicKey.of(node2String.tojstring())))
                ) {
                    iterator.remove();
                }
            }

            return NIL;
        }
    }

    class ClearLinksFunction extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            LuaNetworkTable.this.links.clear();
            LuaNetworkTable.this.nodeLinks.clear();
            return NIL;
        }
    }

    class LinksValue extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            final LuaTable linksTable = tableOf();
            int index = 1;
            for (final LuaLinkTable link : LuaNetworkTable.this.links) {
                linksTable.set(index++, link);
            }
            return linksTable;
        }
    }
}
