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
