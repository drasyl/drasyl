package org.drasyl.cli.sdo.config;

import org.drasyl.cli.sdo.config.ChannelConfig;
import org.drasyl.cli.sdo.config.NodeConfig;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.Multimap;
import org.drasyl.util.Pair;
import org.drasyl.util.network.Subnet;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class NetworkLib extends TwoArgFunction {
    public static final Map<DrasylAddress, NodeConfig> NODES = new HashMap<>();
    public static final Map<Pair<DrasylAddress, DrasylAddress>, ChannelConfig> CHANNELS = new HashMap<>();

    public LuaValue call(final LuaValue modname, final LuaValue env) {
        LuaValue library = tableOf();
        env.set("Network", new Network());
        return library;
    }

    static class Network extends OneArgFunction {
        private final LuaTable nodeDefaults = new LuaTable();
        private final LuaTable linkDefaults = new LuaTable();
        private final Map<String, Node> nodes = new HashMap<>();
        private final Multimap<String, Link> links = new HashSetMultimap<>();

        private Network() {
            nodeDefaults.set("tun_enabled", LuaValue.valueOf(false));
            nodeDefaults.set("tun_name", LuaValue.valueOf("utun0"));
            nodeDefaults.set("tun_subnet", LuaValue.valueOf("10.10.2.0/24"));
            nodeDefaults.set("tun_mtu", LuaValue.valueOf(1225));

            linkDefaults.set("holePunching_enabled", NIL);
            linkDefaults.set("tun_route", LuaValue.valueOf(false));
        }

        @Override
        public LuaValue call(final LuaValue paramsArg) {
            if (paramsArg != null) {
                final LuaTable paramsTable = paramsArg.checktable();
                for (final LuaValue key : paramsTable.keys()) {
                    switch (key.checkstring().tojstring()) {
                        case "node_defaults":
                            final LuaTable nodeDefaults = paramsTable.get(key).checktable();
                            for (final LuaValue key2 : nodeDefaults.keys()) {
                                this.nodeDefaults.set(key2, nodeDefaults.get(key2));
                            }
                            break;
                        case "link_defaults":
                            final LuaTable linkDefaults = paramsTable.get(key).checktable();
                            for (final LuaValue key2 : linkDefaults.keys()) {
                                this.linkDefaults.set(key2, linkDefaults.get(key2));
                            }
                            break;
                        default:
                            throw new LuaError("Param `" + key.checkstring().tojstring() + "` does not exist.");
                    }
                }
            }

            final LuaTable network = tableOf();
            // nodes
            network.set("nodes", new Nodes());
            network.set("add_node", new AddNode());
            network.set("remove_node", new RemoveNode());
            network.set("clear_nodes", new ClearNodes());

            // link
            network.set("add_link", new AddLink());
            network.set("remove_link", new RemoveNode());
            network.set("clear_links", new ClearLinks());

            // internal
            network.set("_network", LuaValue.userdataOf(this));

            return network;
        }

        class Node {
            private final String name;
            private final LuaTable params;

            public Node(final String name, final LuaTable params) {
                this.name = requireNonNull(name);
                // apply defaults first
                for (final LuaValue key : nodeDefaults.keys()) {
                    if (params.get(key) == NIL) {
                        params.set(key, nodeDefaults.get(key));
                    }
                }
                this.params = params;
            }
        }

        class Link {
            private final String node1;
            private final String node2;
            private final LuaTable params;

            public Link(final String node1, final String node2, final LuaTable params) {
                this.node1 = requireNonNull(node1);
                this.node2 = requireNonNull(node2);
                // apply defaults first
                for (final LuaValue key : nodeDefaults.keys()) {
                    if (params.get(key) == NIL) {
                        params.set(key, nodeDefaults.get(key));
                    }
                }
                this.params = params;
            }
        }

        class AddNode extends TwoArgFunction {
            @Override
            public LuaValue call(final LuaValue nameArg, final LuaValue paramsArg) {
                final String nameString = nameArg.checkstring().tojstring();
                final LuaTable paramsTable;
                if (paramsArg == NIL) {
                    paramsTable = tableOf();
                }
                else {
                    paramsTable = paramsArg.checktable();
                }
                final Node node = new Node(nameString, paramsTable);
                Network.this.nodes.put(nameString, node);
                return NIL;
            }
        }

        class RemoveNode extends OneArgFunction {
            @Override
            public LuaValue call(final LuaValue nameArg) {
                final String nameString = nameArg.checkstring().tojstring();
                final boolean removed = Network.this.nodes.remove(nameString) != null;
                return LuaValue.valueOf(removed);
            }
        }

        class ClearNodes extends ZeroArgFunction {
            @Override
            public LuaValue call() {
                Network.this.nodes.clear();
                return NIL;
            }
        }

        class AddLink extends ThreeArgFunction {
            @Override
            public LuaValue call(final LuaValue node1Arg, final LuaValue node2Arg, final LuaValue paramsArg) {
                final String node1String = node1Arg.checkstring().tojstring();
                final String node2String = node2Arg.checkstring().tojstring();
                if (!Network.this.nodes.containsKey(node1String)) {
                    throw new LuaError("Node `" + node1String + "` does not exist.");
                }
                if (!Network.this.nodes.containsKey(node2String)) {
                    throw new LuaError("Node `" + node2String + "` does not exist.");
                }
                final LuaTable paramsTable;
                if (paramsArg == NIL) {
                    paramsTable = tableOf();
                }
                else {
                    paramsTable = paramsArg.checktable();
                }
                final Link link = new Link(node1String, node2String, paramsTable);
                Network.this.links.put(node1String, link);
                Network.this.links.put(node2String, link);

                return NIL;
            }
        }

        class ClearLinks extends ZeroArgFunction {
            @Override
            public LuaValue call() {
                Network.this.links.clear();
                return NIL;
            }
        }

        class Nodes extends ZeroArgFunction {
            @Override
            public LuaValue call() {
                return new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        final LuaTable nodesTable = tableOf();
                        int index = 0;
                        for (final Node node : Network.this.nodes.values()) {
                            final LuaTable nodeTable = tableOf();
                            nodesTable.set(index++, nodeTable);
                        }
                        return nodesTable;
                    }
                };
            }
        }
    }

    static class AddNode extends TwoArgFunction {
        public LuaValue call(final LuaValue arg1, final LuaValue arg2) {
            try {
                final LuaString name = arg1.checkstring();
                final Map<String, Object> optsMap = new HashMap<>();
                if (arg2 != NIL) {
                    final LuaTable opts = arg2.checktable();
                    for (final LuaValue key : opts.keys()) {
                        final String keyString = key.tojstring();
                        final Object value;
                        switch (keyString) {
                            case "tun_enabled":
                                value = opts.get(key).toboolean();
                                break;
                            case "tun_name":
                                value = opts.get(key).tojstring();
                                break;
                            case "tun_subnet":
                                value = new Subnet(opts.get(key).tojstring());
                                break;
                            case "tun_address":
                                value = InetAddress.getByName(opts.get(key).tojstring());
                                break;
                            case "tun_mtu":
                                value = opts.get(key).toint();
                                break;
                            case "extend":
                                // ignore
                                continue;
                            default:
                                throw new RuntimeException("Unknown key: " + keyString);
                        }

                        optsMap.put(keyString, value);
                    }
                }
                NODES.put(IdentityPublicKey.of(name.tojstring()), new NodeConfig(optsMap));

                return NIL;
            }
            catch (final UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class AddChannel extends ThreeArgFunction {
        public LuaValue call(final LuaValue arg1, final LuaValue arg2, final LuaValue arg3) {
            final LuaString node1 = arg1.checkstring();
            final LuaString node2 = arg2.checkstring();
            final Map<String, Object> optsMap = new HashMap<>();
            if (arg3 != NIL) {
                final LuaTable opts = arg3.checktable();
                for (final LuaValue key : opts.keys()) {
                    final String keyString = key.tojstring();
                    final Object value;
                    switch (keyString) {
                        case "directPath":
                            if (opts.get(key) == NIL) {
                                value = null;
                            }
                            else {
                                value = opts.get(key).toboolean();
                            }
                            break;
                        case "tun_route":
                            value = opts.get(key).toboolean();
                            break;
                        case "extend":
                            // ignore
                            continue;
                        default:
                            throw new RuntimeException("Unknown key: " + keyString);
                    }

                    optsMap.put(keyString, value);
                }
            }
            final Pair<DrasylAddress, DrasylAddress> key = Pair.of(IdentityPublicKey.of(node1.tojstring()), IdentityPublicKey.of(node2.tojstring()));
            CHANNELS.put(key, new ChannelConfig(optsMap));

            return NIL;
        }
    }
}
