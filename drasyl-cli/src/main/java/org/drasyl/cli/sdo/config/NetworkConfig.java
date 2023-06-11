package org.drasyl.cli.sdo.config;

import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class NetworkConfig {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfig.class);
    private final LuaNetworkTable network;

    public NetworkConfig(final LuaNetworkTable network) {
        this.network = requireNonNull(network);
    }

    public static NetworkConfig parseFile(final File file) throws IOException {
        LOG.debug("Load network config from `{}`", file);
        LuaValue chunk = globals().loadfile(file.toString());
        chunk.call();

        return new NetworkConfig(ControllerLib.NETWORK);
    }

    private static Globals globals() {
        Globals globals = JsePlatform.standardGlobals();
        globals.load(new NetworkLib());
        globals.load(new ControllerLib());
        return globals;
    }

    public LuaNetworkTable network() {
        return network;
    }
}
