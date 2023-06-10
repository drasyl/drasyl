package org.drasyl.cli.sdo.config;

import java.util.Map;

public class ChannelConfig {
    private final Boolean directPath;
    private final boolean tunRoute;

    public ChannelConfig(final Map<String, Object> map) {
        directPath = (Boolean) map.get("directPath");
        tunRoute = (boolean) map.get("tun_route");
    }

    public Boolean isDirectPath() {
        return directPath;
    }

    public boolean isTunRoute() {
        return tunRoute;
    }
}
