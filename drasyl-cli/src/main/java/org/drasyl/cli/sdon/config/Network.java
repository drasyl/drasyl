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
public class Network extends LuaTable {
    private final Map<LuaString, NetworkNode> nodes = new HashMap<>();
    private final Set<NetworkLink> links = new HashSet<>();
    final SetMultimap<LuaString, NetworkLink> nodeLinks = new HashSetMultimap<>();
    private final Devices devices = new Devices();
    private int nextIpIndex;
    private LuaFunction callback;

    public Network(final LuaTable params) {
        LuaValue subnet = params.get("subnet");
        if (subnet == NIL) {
            subnet = LuaValue.valueOf("10.0.0.0/8");
        }
        set("subnet", subnet);

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

    public Network(final LuaValue params) {
        this(params == NIL ? tableOf() : params.checktable());
    }

    @Override
    public String toString() {
        final LuaTable stringTable = tableOf();
        stringTable.set("nodes", getNodesTable());
        stringTable.set("links", getLinks());
        return "Network" + LuaStrings.toString(stringTable);
    }

    /*
     * Nodes
     */

    public Map<LuaString, NetworkNode> getNodes() {
        return nodes;
    }

    private LuaTable getNodesTable() {
        return LuaHelper.createTable(nodes.values());
    }

    private LuaValue getNode(final LuaString nameString) {
        final NetworkNode networkNode = nodes.get(nameString);

        if (networkNode == null) {
            return NIL;
        }
        return networkNode;
    }

    private NetworkNode addNode(final LuaString name, final LuaTable params) {
        final NetworkNode node = new NetworkNode(this, name, params);

        nodes.put(name, node);
        return node;
    }

    private LuaValue removeNode(final LuaString name) {
        final NetworkNode networkNode = nodes.remove(name);

        if (networkNode == null) {
            return NIL;
        }

        // remove all node links
        nodeLinks.get(name).forEach(nodeLink -> {
            links.remove(nodeLink);
            nodeLinks.remove(name, nodeLink);
        });

        return networkNode;
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
        for (final NetworkLink link : links) {
            linksTable.set(index++, link);
        }
        return linksTable;
    }

    private LuaValue getLink(final LuaString node1String, final LuaString node2String) {
        final Iterator<NetworkLink> iterator = links.iterator();
        while (iterator.hasNext()) {
            final NetworkLink link = iterator.next();

            if ((link.node1().equals(node1String) && link.node2().equals(node1String)) || (link.node1().equals(node2String) && link.node2().equals(node2String))) {
                return link;
            }
        }

        return NIL;
    }

    private LuaBoolean addLink(final LuaString node1String,
                               final LuaString node2String,
                               final LuaTable params) {
        final NetworkLink link = new NetworkLink(this, node1String, node2String, params);

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

        final Iterator<NetworkLink> iterator = links.iterator();
        while (iterator.hasNext()) {
            final NetworkLink link = iterator.next();

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
        final Network that = (Network) o;
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

    private Devices getDevicesTable() {
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
            final Network network = (Network) networkArg.checktable();

            return network.getNodesTable();
        }
    }

    static class GetNodeFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg, final LuaValue nameArg) {
            final Network network = (Network) networkArg.checktable();
            final LuaString nameString = nameArg.checkstring();

            return network.getNode(nameString);
        }
    }

    static class AddNodeFunction extends ThreeArgFunction {
        @Override
        public NetworkNode call(final LuaValue networkArg,
                                final LuaValue nameArg,
                                final LuaValue paramsArg) {
            final Network network = (Network) networkArg.checktable();
            final LuaString nameString = nameArg.checkstring();
            final LuaTable paramsTable = paramsArg.checktable();

            return network.addNode(nameString, paramsTable);
        }

        @Override
        public NetworkNode call(final LuaValue networkArg, final LuaValue nameArg) {
            return call(networkArg, nameArg, tableOf());
        }
    }

    static class RemoveNodeFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg, final LuaValue nameArg) {
            final Network network = (Network) networkArg.checktable();
            final LuaString nameString = nameArg.checkstring();

            return network.removeNode(nameString);
        }
    }

    static class ClearNodesFunction extends OneArgFunction {
        @Override
        public LuaNil call(final LuaValue networkArg) {
            final Network network = (Network) networkArg.checktable();

            return network.clearNodes();
        }
    }

    /*
     * Links
     */

    static class GetLinksFunction extends OneArgFunction {
        @Override
        public LuaTable call(final LuaValue networkArg) {
            final Network network = (Network) networkArg.checktable();

            return network.getLinks();
        }
    }

    static class GetLinkFunction extends ThreeArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg,
                             final LuaValue node1Arg,
                             final LuaValue node2Arg) {
            final Network network = (Network) networkArg.checktable();
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();

            return network.getLink(node1String, node2String);
        }
    }

    static class AddLinkFunction extends LibFunction {
        @Override
        public LuaBoolean call(final LuaValue networkArg,
                               final LuaValue node1Arg,
                               final LuaValue node2Arg,
                               final LuaValue paramsArg) {
            final Network network = (Network) networkArg.checktable();
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();
            final LuaTable paramsTable = paramsArg.checktable();

            if (network.getNode(node1String) == NIL) {
                throw new LuaError("Node `" + node1String + "` does not exist.");
            }
            if (network.getNode(node2String) == NIL) {
                throw new LuaError("Node `" + node2String + "` does not exist.");
            }

            return network.addLink(node1String, node2String, paramsTable);
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
            final Network network = (Network) networkArg.checktable();
            final LuaString node1String = node1Arg.checkstring();
            final LuaString node2String = node2Arg.checkstring();

            return network.removeLink(node1String, node2String);
        }
    }

    static class ClearLinksFunction extends OneArgFunction {
        @Override
        public LuaNil call(final LuaValue networkArg) {
            final Network network = (Network) networkArg.checktable();

            return network.clearLinks();
        }
    }

    static class SetCallbackFunction extends TwoArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg, final LuaValue callbackArg) {
            final Network network = (Network) networkArg.checktable();
            final LuaFunction callbackFunction = callbackArg.checkfunction();

            network.setCallback(callbackFunction);

            return NIL;
        }
    }
}
