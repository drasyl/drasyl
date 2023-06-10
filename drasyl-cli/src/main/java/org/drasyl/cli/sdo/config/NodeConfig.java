package org.drasyl.cli.sdo.config;

import org.drasyl.util.network.Subnet;

import java.net.InetAddress;
import java.util.Map;

public class NodeConfig {
    private final boolean tunEnabled;
    private final String tunName;
    private final Subnet tunSubnet;
    private final int tunMtu;
    private final InetAddress tunAddress;

    public NodeConfig(final Map<String, Object> map) {
        tunEnabled = (boolean) map.get("tun_enabled");
        tunName = (String) map.get("tun_name");
        tunSubnet = (Subnet) map.get("tun_subnet");
        tunMtu = (int) map.get("tun_mtu");
        tunAddress = (InetAddress) map.get("tun_address");
    }

    public boolean isTunEnabled() {
        return tunEnabled;
    }

    public String getTunName() {
        return tunName;
    }

    public Subnet getTunSubnet() {
        return tunSubnet;
    }

    public int getTunMtu() {
        return tunMtu;
    }

    public InetAddress getTunAddress() {
        return tunAddress;
    }
}
