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
import org.drasyl.cli.util.LuaHashCodes;
import org.drasyl.cli.util.LuaStrings;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.net.SocketAddress;
import java.util.*;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;

@SuppressWarnings("java:S110")
public class LuaNetworkTable extends LuaTable {
    private static final Logger LOG = LoggerFactory.getLogger(LuaNetworkTable.class);
    final LuaTable nodeDefaults = new LuaTable();
    final LuaTable linkDefaults = new LuaTable();
    final Map<DrasylAddress, LuaNodeTable> nodes = new HashMap<>();
    final Set<LuaLinkTable> links = new HashSet<>();
    final SetMultimap<DrasylAddress, LuaLinkTable> nodeLinks = new HashSetMultimap<>();
    private LuaClosure networkListener;

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
                "nodes=" + nodes +
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
        LuaNetworkTable that = (LuaNetworkTable) o;
        return Objects.equals(nodes, that.nodes) && Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, links);
    }

    public boolean notifyListener(final ChannelHandlerContext ctx) {
        if (networkListener != null) {
            final int before = hashCode();
            networkListener.call(this);
            final int after = hashCode();
            boolean networkChanged = before != after;

            if (networkChanged) {
                LOG.trace("Network has changed. Push new policies to all online nodes.");

                // push new config to all nodes
                final Map<SocketAddress, DrasylChannel> channels = ((DrasylServerChannel) ctx.channel()).channels;
                for (final LuaNodeTable node : nodes.values()) {
                    final DrasylChannel channel = channels.get(node.name());
                    if (node.state().isOnline()) {
                        final ControllerHello controllerHello = new ControllerHello(node.policies());
                        LOG.trace("Send {} to {}.", controllerHello, node.name());
                        channel.writeAndFlush(controllerHello).addListener(FIRE_EXCEPTION_ON_FAILURE);;
                    }
                }
            }

            return networkChanged;
        }

        return false;
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
            LuaNetworkTable.this.links.add(link);
            LuaNetworkTable.this.nodeLinks.put(link.node1(), link);
            LuaNetworkTable.this.nodeLinks.put(link.node2(), link);

            return NIL;
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

            Iterator<LuaLinkTable> iterator = LuaNetworkTable.this.links.iterator();

            while (iterator.hasNext()) {
                LuaLinkTable link = iterator.next();

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
}
