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

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.cli.util.LuaHelper;
import org.drasyl.cli.util.LuaStrings;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaNil;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("java:S110")
public class NetworkTable extends LuaTable {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkTable.class);
    private final Map<LuaString, NodeTable> nodes = new HashMap<>();
    private final Set<LinkTable> links = new HashSet<>();
    final SetMultimap<LuaString, LinkTable> nodeLinks = new HashSetMultimap<>();
    private final DevicesTable devices = new DevicesTable();
    private int nextIpIndex;
    private LuaFunction callback;

    public NetworkTable(final LuaTable params) {
        LuaValue subnet = params.get("subnet");
        if (subnet == NIL) {
            subnet = LuaValue.valueOf("10.0.0.0/8");
        }
        set("subnet", subnet);

//        nodeDefaults.set("default_route", NIL);
//
//        nodeDefaults.set("tun_enabled", LuaValue.valueOf(false));
//        nodeDefaults.set("tun_name", LuaValue.valueOf("utun0"));
//        nodeDefaults.set("tun_subnet", LuaValue.valueOf("10.10.2.0/24"));
//        nodeDefaults.set("tun_mtu", LuaValue.valueOf(1225));
//        nodeDefaults.set("tun_routes", tableOf());
//        // FIXME: add tun_address default
//
//        for (final LuaValue key : params.keys()) {
//            switch (key.checkstring().tojstring()) {
//                case "node_defaults":
//                    final LuaTable nodeDefaults = params.get(key).checktable();
//                    for (final LuaValue key2 : nodeDefaults.keys()) {
//                        this.nodeDefaults.set(key2, nodeDefaults.get(key2));
//                    }
//                    break;
//                case "link_defaults":
//                    final LuaTable linkDefaults = params.get(key).checktable();
//                    for (final LuaValue key2 : linkDefaults.keys()) {
//                        this.linkDefaults.set(key2, linkDefaults.get(key2));
//                    }
//                    break;
//                case "network_listener":
//                    this.networkListener = (LuaClosure) params.get(key).checkfunction();
//                    break;
//                case "proactive_latency_measurements_ratio":
//                    this.proactiveLatencyMeasurementsRatio = params.get(key).checknumber();
//                    break;
//                case "proactive_latency_measurements_interval":
//                    this.proactiveLatencyMeasurementsInterval = params.get(key).checknumber();
//                    break;
//                case "proactive_latency_measurements_candidates":
//                    this.proactiveLatencyMeasurementsCandidates = params.get(key).checktable();
//                    break;
//                default:
//                    throw new LuaError("Param `" + key.checkstring().tojstring() + "` does not exist.");
//            }
//        }

        // nodes
        set("get_nodes", new GetNodesFunction());
        set("get_node", new GetNodeFunction());
        set("add_node", new AddNodeFunction());
        set("remove_node", new RemoveNodeFunction());
        set("clear_nodes", new ClearNodesFunction());

        // link
        set("get_links", new GetLinksFunction());
        set("get_link", new GetLinkFunction());
        set("add_link", new AddLinkFunction());
        set("remove_link", new RemoveLinkFunction());
        set("clear_links", new ClearLinksFunction());

        // network
        set("set_callback", new SetCallbackFunction());
    }

    public NetworkTable(final LuaValue params) {
        this(params == NIL ? tableOf() : params.checktable());
    }

    @Override
    public String toString() {
        final LuaTable publicTable = tableOf();
        publicTable.set("nodes", getNodesTable());
        publicTable.set("links", getLinks());
        return "Network" + LuaStrings.toString(publicTable);
    }

    /*
     * Nodes
     */

    public Map<LuaString, NodeTable> getNodes() {
        return nodes;
    }

    private LuaTable getNodesTable() {
        return LuaHelper.createTable(nodes.values());
    }

    private LuaValue getNode(final LuaString nameString) {
        final NodeTable nodeTable = nodes.get(nameString);

        if (nodeTable == null) {
            return NIL;
        }
        return nodeTable;
    }

    private NodeTable addNode(final LuaString name, final LuaTable params) {
        final NodeTable node = new NodeTable(this, name, params);

        nodes.put(name, node);
        return node;
    }

    private LuaValue removeNode(final LuaString name) {
        final NodeTable nodeTable = nodes.remove(name);

        if (nodeTable == null) {
            return NIL;
        }

        // remove all node links
        nodeLinks.get(name).forEach(nodeLink -> {
            links.remove(nodeLink);
            nodeLinks.remove(name, nodeLink);
        });

        return nodeTable;
    }

    private LuaNil clearNodes() {
        nodes.clear();
        links.clear();
        nodeLinks.clear();

        return (LuaNil) NIL;
    }

    /*
     * Links
     */

    private LuaTable getLinks() {
        final LuaTable linksTable = tableOf();
        int index = 1;
        for (final LinkTable link : links) {
            linksTable.set(index++, link);
        }
        return linksTable;
    }

    private LuaValue getLink(final LuaString node1String, final LuaString node2String) {
        final Iterator<LinkTable> iterator = links.iterator();
        while (iterator.hasNext()) {
            final LinkTable link = iterator.next();

            if ((link.node1().equals(node1String) && link.node2().equals(node1String)) || (link.node1().equals(node2String) && link.node2().equals(node2String))) {
                return link;
            }
        }

        return NIL;
    }

    private LuaBoolean addLink(final LuaString node1String,
                               final LuaString node2String,
                               final LuaTable params) {
        final LinkTable link = new LinkTable(this, node1String, node2String, params);

        final boolean newLink = links.add(link);
        nodeLinks.put(link.node1(), link);
        nodeLinks.put(link.node2(), link);

        return newLink ? TRUE : FALSE;
    }

    private LuaNil removeLink(final LuaString node1String, final LuaString node2String) {
        if (!nodes.containsKey(node1String)) {
            throw new LuaError("Node `" + node1String + "` does not exist.");
        }
        if (!nodes.containsKey(node2String)) {
            throw new LuaError("Node `" + node2String + "` does not exist.");
        }

        final Iterator<LinkTable> iterator = links.iterator();
        while (iterator.hasNext()) {
            final LinkTable link = iterator.next();

            if ((link.node1().equals(node1String) && link.node2().equals(node1String)) || (link.node1().equals(node2String) && link.node2().equals(node2String))) {
                iterator.remove();
            }
        }

        return (LuaNil) NIL;
    }

    private LuaNil clearLinks() {
        links.clear();
        nodeLinks.clear();

        return (LuaNil) NIL;
    }

    private LuaNil setCallback(final LuaFunction callback) {
        this.callback = callback;

        return (LuaNil) NIL;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final NetworkTable that = (NetworkTable) o;
        return Objects.equals(nodes, that.nodes) && Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, links);
    }

    public boolean notifyListener(final ChannelHandlerContext ctx) throws IOException {
//        if (networkListener != null) {
//            final int before = hashCode();
//            networkListener.call(this);
//            final int after = hashCode();
//            final boolean networkChanged = before != after;
//
//            if (networkChanged) {
//                LOG.trace("Network has changed. Push new policies to all online nodes.");
//
//                // push new config to all nodes
//                final Map<DrasylAddress, DrasylChannel> channels = ((DrasylServerChannel) ctx.channel()).getChannels();
//                for (final NodeTable node : nodes.values()) {
//                    final DrasylChannel channel = channels.get(node.name());
//                    if (node.state().isOnline()) {
//                        final Set<Policy> policies = node.policies();
//
//                        final ControllerHello controllerHello = new ControllerHello(policies);
//                        LOG.error("Send {} to {}.", controllerHello, node.name());
//                        channel.writeAndFlush(controllerHello).addListener(FIRE_EXCEPTION_ON_FAILURE);
//                    }
//                }
//            }
//
//            return networkChanged;
//        }

        return false;
    }

    public String getNextIp() {
        try {
            final Subnet subnet = new Subnet(get("subnet").tojstring());
            return subnet.nth(nextIpIndex++).getHostAddress() + "/" + subnet.netmaskLength();
        }
        catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void callCallback() {
        if (callback != null) {
            callback.call(this, getDevicesTable());
        }
    }

    public Collection<Device> getDevices() {
        return devices.getDevices();
    }

    private DevicesTable getDevicesTable() {
        return devices;
    }

    public Device getOrCreateDevice(final DrasylAddress address, final String[] tags) {
        return devices.getOrCreateDevice(address, tags);
    }

    /*
     * Nodes
     */

    static class GetNodesFunction extends OneArgFunction {
        @Override
        public LuaTable call(final LuaValue networkArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();

            return networkTable.getNodesTable();
        }
    }

    static class GetNodeFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg, final LuaValue nameArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaString nameString = nameArg.checkstring();

            return networkTable.getNode(nameString);
        }
    }

    static class AddNodeFunction extends ThreeArgFunction {
        @Override
        public NodeTable call(final LuaValue networkArg,
                             final LuaValue nameArg,
                             final LuaValue paramsArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaString nameString = nameArg.checkstring();
            final LuaTable paramsTable = paramsArg.checktable();

            return networkTable.addNode(nameString, paramsTable);
        }

        @Override
        public NodeTable call(final LuaValue networkArg, final LuaValue nameArg) {
            return call(networkArg, nameArg, tableOf());
        }
    }

    static class RemoveNodeFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg, final LuaValue nameArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaString nameString = nameArg.checkstring();

            return networkTable.removeNode(nameString);
        }
    }

    static class ClearNodesFunction extends OneArgFunction {
        @Override
        public LuaNil call(final LuaValue networkArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();

            return networkTable.clearNodes();
        }
    }

    /*
     * Links
     */

    static class GetLinksFunction extends OneArgFunction {
        @Override
        public LuaTable call(final LuaValue networkArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();

            return networkTable.getLinks();
        }
    }

    static class GetLinkFunction extends ThreeArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg,
                             final LuaValue node1Arg,
                             final LuaValue node2Arg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();

            return networkTable.getLink(node1String, node2String);
        }
    }

    static class AddLinkFunction extends LibFunction {
        @Override
        public LuaBoolean call(final LuaValue networkArg,
                               final LuaValue node1Arg,
                               final LuaValue node2Arg,
                               final LuaValue paramsArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();
            final LuaTable paramsTable = paramsArg.checktable();

            if (networkTable.getNode(node1String) == NIL) {
                throw new LuaError("Node `" + node1String + "` does not exist.");
            }
            if (networkTable.getNode(node2String) == NIL) {
                throw new LuaError("Node `" + node2String + "` does not exist.");
            }

            return networkTable.addLink(node1String, node2String, paramsTable);
        }

        @Override
        public LuaBoolean call(final LuaValue networkArg,
                               final LuaValue node1Arg,
                               final LuaValue node2Arg) {
            return call(networkArg, node1Arg, node2Arg, tableOf());
        }
    }

    static class RemoveLinkFunction extends ThreeArgFunction {
        @Override
        public LuaNil call(final LuaValue networkArg,
                           final LuaValue node1Arg,
                           final LuaValue node2Arg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();

            return networkTable.removeLink(node1String, node2String);
        }
    }

    static class ClearLinksFunction extends OneArgFunction {
        @Override
        public LuaNil call(final LuaValue networkArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();

            return networkTable.clearLinks();
        }
    }

    static class SetCallbackFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg, final LuaValue callbackArg) {
            final NetworkTable networkTable = (NetworkTable) networkArg.checktable();
            final LuaFunction callbackFunction = callbackArg.checkfunction();

            networkTable.setCallback(callbackFunction);

            return NIL;
        }
    }
}
