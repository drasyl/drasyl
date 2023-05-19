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
package org.drasyl.cli.sdo.handler;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;
import org.drasyl.channel.tun.jna.windows.Wintun;
import org.drasyl.cli.sdo.NetworkConfig;
import org.drasyl.cli.tun.jna.AddressAndNetmaskHelper;
import org.drasyl.handler.path.DirectPathHandler;
import org.drasyl.handler.path.NoDirectPathHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.TunChannelOption.TUN_MTU;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;
import static org.drasyl.util.Preconditions.requirePositive;

public class NetworkConfigurationHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfigurationHandler.class);
    private final NetworkConfig config;

    public NetworkConfigurationHandler(final NetworkConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        // FIXME: erstelle NUR für gewünschte KNOTEN direkt links
        // Behaviral traversal handler?
        // direct verbindung aufbauen auf befehl
        final Set<SocketAddress> consideredPeers = new HashSet<>();
        consideredPeers.add(ctx.channel().localAddress());
        final ConfigList list = config.config.getList("network.connections");
        for (ConfigValue value : list) {
            final Map<String, Object> attributes = (Map<String, Object>) value.unwrapped();
            final String fromAddress = (String) attributes.get("from");
            final IdentityPublicKey fromKey = IdentityPublicKey.of(fromAddress);
            final String toAddress = (String) attributes.get("to");
            final IdentityPublicKey toKey = IdentityPublicKey.of(toAddress);

            if (ctx.channel().localAddress().equals(fromKey)) {
                // direct path
                if (consideredPeers.add(toKey)) {
                    ctx.pipeline().addAfter(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new DirectPathHandler(toKey));
                }
            }
            else if (ctx.channel().localAddress().equals(toKey)) {
                // direct path
                if (consideredPeers.add(fromKey)) {
                    ctx.pipeline().addAfter(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new DirectPathHandler(fromKey));
                }
            }
            else {
                // no direct path
                if (consideredPeers.add(fromKey)) {
                    ctx.pipeline().addBefore(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new NoDirectPathHandler(fromKey));
                }
                if (consideredPeers.add(toKey)) {
                    ctx.pipeline().addBefore(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new NoDirectPathHandler(toKey));
                }
            }
        }

        // TUN?
        final Map<String, Object> node = config.getNode((DrasylAddress) ctx.channel().localAddress());
        if (node.containsKey("tun")) {
            final String name = (String) ((Map<String, Object>) node.get("tun")).get("name");
            final Subnet subnet = new Subnet((String) ((Map<String, Object>) node.get("tun")).get("subnet"));
            final InetAddress inetAddress = InetAddress.getByName((String) ((Map<String, Object>) node.get("tun")).get("address"));
            final int mtu = 1225;

            final Map<InetAddress, DrasylAddress> routes = new HashMap<>();
            final List<Map<String, Object>> connections = config.getConnectionsFor((DrasylAddress) ctx.channel().localAddress());
            for (final Map<String, Object> connection : connections) {
                final String fromAddress = (String) connection.get("from");
                final IdentityPublicKey fromKey = IdentityPublicKey.of(fromAddress);
                final String toAddress = (String) connection.get("to");
                final IdentityPublicKey toKey = IdentityPublicKey.of(toAddress);
                final IdentityPublicKey peerKey;
                if (ctx.channel().localAddress().equals(fromKey)) {
                    peerKey = toKey;
                }
                else {
                    peerKey = fromKey;
                }

                final Map<String, Object> peer = config.getNode(peerKey);
                final InetAddress peerInetAddress = InetAddress.getByName((String) ((Map<String, Object>) peer.get("tun")).get("address"));
                routes.put(peerInetAddress, peerKey);
            }

            final Bootstrap b = new Bootstrap()
                    .channel(TunChannel.class)
                    .option(AUTO_READ, true)
                    .option(TUN_MTU, mtu)
                    .group(new DefaultEventLoopGroup(1))
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new TunHandler((IdentityPublicKey) ctx.channel().localAddress(), subnet, inetAddress, 1225));
                        }
                    });
//            final Channel ch = b.bind(new TunAddress(name)).syncUninterruptibly().channel();
        }
    }

    /**
     * Assign IP address and subnet to the tun device.
     */
    public class TunHandler extends ChannelInboundHandlerAdapter {
        private final IdentityPublicKey address;
        private final InetAddress inetAddress;
        private final Subnet subnet;
        private final int mtu;

        public TunHandler(final IdentityPublicKey address,
                          final Subnet subnet,
                          final InetAddress inetAddress,
                          final int mtu) {
            this.address = requireNonNull(address);
            this.subnet = requireNonNull(subnet);
            this.inetAddress = requireNonNull(inetAddress);
            this.mtu = requirePositive(mtu);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws IOException {
            ctx.fireChannelActive();

            // configure network device
            System.out.print("Created network device '" + ctx.channel().localAddress() + "'. Now assign address " + inetAddress.getHostAddress() + " with netmask " + subnet.netmaskLength() + " to it...");
            configurateTun((TunChannel) ctx.channel(), ctx.channel().localAddress().toString());
            System.out.println("done!");

            System.out.println("Network device is ready!");
            System.out.println();

            ctx.pipeline().remove(ctx.name());
        }

        private void configurateTun(final TunChannel channel,
                                    final String name) throws IOException {
            final String addressStr = inetAddress.getHostAddress();
            if (PlatformDependent.isOsx()) {
                // macOS
                exec("/sbin/ifconfig", name, "add", addressStr, addressStr);
                exec("/sbin/ifconfig", name, "up");
                exec("/sbin/route", "add", "-net", subnet.toString(), "-iface", name);
            }
            else if (PlatformDependent.isWindows()) {
                // Windows
                final Wintun.WINTUN_ADAPTER_HANDLE adapter = ((WindowsTunDevice) channel.device()).adapter();

                final Pointer interfaceLuid = new Memory(8);
                WintunGetAdapterLUID(adapter, interfaceLuid);
                AddressAndNetmaskHelper.setIPv4AndNetmask(interfaceLuid, addressStr, subnet.netmaskLength());

                exec("netsh", "interface", "ipv4", "set", "subinterface", name, "mtu=" + mtu, "store=active");
            }
            else {
                // Linux
                exec("/sbin/ip", "addr", "add", addressStr + "/" + subnet.netmaskLength(), "dev", name);
                exec("/sbin/ip", "link", "set", "dev", name, "up");
            }
        }

        private void exec(final String... command) throws IOException {
            try {
                LOG.trace("Execute: {}", String.join(" ", command));
                final int exitCode = Runtime.getRuntime().exec(command).waitFor();
                if (exitCode != 0) {
                    throw new IOException("Executing `" + String.join(" ", command) + "` returned non-zero exit code (" + exitCode + ").");
                }
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
