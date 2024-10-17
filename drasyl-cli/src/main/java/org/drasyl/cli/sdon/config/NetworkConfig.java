package org.drasyl.cli.sdon.config;

import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class NetworkConfig {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfig.class);
    private final NetworkTable network;

    public NetworkConfig(final NetworkTable network) {
        this.network = requireNonNull(network);
    }

    public static NetworkConfig parseFile(final File file) throws IOException {
        LOG.debug("Load network config from `{}`", file);
        if (!file.exists()) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
        final LuaValue chunk = globals().loadfile(file.toString());
        chunk.call();

        if (ControllerLib.NETWORK == null) {
            throw new IOException("No network has been registered. Have you called register_network(net)?");
        }

        return new NetworkConfig(ControllerLib.NETWORK);
    }

    private static Globals globals() {
        final Globals globals = JsePlatform.standardGlobals();
        globals.load(new ControllerLib());
        return globals;
    }

    public NetworkTable network() {
        return network;
    }
}
