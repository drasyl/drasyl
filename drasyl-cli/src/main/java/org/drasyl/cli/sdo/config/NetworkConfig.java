package org.drasyl.cli.sdo.config;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class NetworkConfig {
    private final String lua;
    private final Map<DrasylAddress, NodeConfig> nodes;
    private final Map<Pair<DrasylAddress, DrasylAddress>, ChannelConfig> channels;

    public NetworkConfig(final String lua, final Map<DrasylAddress, NodeConfig> nodes, final Map<Pair<DrasylAddress, DrasylAddress>, ChannelConfig> channels) {
        this.lua = requireNonNull(lua);
        this.nodes = requireNonNull(nodes);
        this.channels = requireNonNull(channels);
    }

    @Override
    public String toString() {
        return lua;
    }

    public static NetworkConfig parseFile(final File file) {
        Globals globals = globals();
        LuaValue chunk = globals.loadfile(file.toString());
        chunk.call();

        try {
            String lua = new String(Files.readAllBytes(file.toPath()));
        return new NetworkConfig(lua, NetworkLib.NODES, NetworkLib.CHANNELS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    public static NetworkConfig parseString(String s) {
        Globals globals = globals();
        LuaValue chunk = globals.load(s);
        chunk.call();

        return new NetworkConfig(s, NetworkLib.NODES, NetworkLib.CHANNELS);
    }

    private static Globals globals() {
        Globals globals = JsePlatform.standardGlobals();
        globals.load(new UtilLib());
        globals.load(new NetworkLib());
        globals.load(new NodeLib());
        globals.load(new ChannelLib());
        globals.load(new ControllerLib());
        return globals;
    }

    public NodeConfig getNode(final DrasylAddress address) {
        return nodes.get(address);
    }

    public boolean isNode(final DrasylAddress address) {
        return nodes.containsKey(address);
    }

    public Map<Pair<DrasylAddress, DrasylAddress>, ChannelConfig> getChannels() {
        return channels;
    }
}
