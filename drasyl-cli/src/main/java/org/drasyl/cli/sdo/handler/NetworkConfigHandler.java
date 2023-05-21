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
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;
import org.drasyl.channel.tun.jna.windows.Wintun;
import org.drasyl.cli.sdo.NetworkConfig;
import org.drasyl.cli.tun.jna.AddressAndNetmaskHelper;
import org.drasyl.crypto.HexUtil;
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
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.TunChannelOption.TUN_MTU;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;
import static org.drasyl.util.Preconditions.requirePositive;

public class NetworkConfigHandler extends ChannelInboundHandlerAdapter {
    public static final AttributeKey<TunChannel> TUN_CHANNEL_KEY = AttributeKey.valueOf("TUN_CHANNEL_KEY");
    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfigHandler.class);
    private final NetworkConfig config;
    private boolean applied;
    private Channel tunChannel;

    public NetworkConfigHandler(final NetworkConfig config) {
        this.config = requireNonNull(config);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws UnknownHostException {
        if (!applied && ctx.channel().isActive()) {
            applied = true;
            applyConfiguration(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        if (!applied) {
            applied = true;
            applyConfiguration(ctx);
        }

        ctx.fireChannelActive();
    }

    private void applyConfiguration(final ChannelHandlerContext ctx) throws UnknownHostException {
        // try to establish a direct path to all nodes we have a connection to
        // prevent direct paths to all other nodes of our network
        final Set<SocketAddress> consideredPeers = new HashSet<>();
        consideredPeers.add(ctx.channel().localAddress());
        final ConfigList list = config.config.getList("network.connections");
        for (ConfigValue value : list) {
            final Map<String, Object> attributes = (Map<String, Object>) value.unwrapped();
            final String fromAddress = (String) attributes.get("from");
            final IdentityPublicKey fromKey = IdentityPublicKey.of(fromAddress);
            final String toAddress = (String) attributes.get("to");
            final IdentityPublicKey toKey = IdentityPublicKey.of(toAddress);
            final Boolean directPath = (Boolean) attributes.getOrDefault("direct-path", null);

            if (Boolean.TRUE.equals(directPath) && ctx.channel().localAddress().equals(fromKey)) {
                // direct path
                if (consideredPeers.add(toKey)) {
                    ctx.pipeline().addAfter(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new DirectPathHandler(toKey));
                }
            }
            else if (Boolean.TRUE.equals(directPath) && ctx.channel().localAddress().equals(toKey)) {
                // direct path
                if (consideredPeers.add(fromKey)) {
                    ctx.pipeline().addAfter(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new DirectPathHandler(fromKey));
                }
            }
            else if (Boolean.FALSE.equals(directPath)) {
                // no direct path
                if (consideredPeers.add(fromKey)) {
                    ctx.pipeline().addBefore(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new NoDirectPathHandler(fromKey));
                }
                if (consideredPeers.add(toKey)) {
                    ctx.pipeline().addBefore(ctx.pipeline().context(TraversingInternetDiscoveryChildrenHandler.class).name(), null, new NoDirectPathHandler(toKey));
                }
            }
        }

        final Map<String, Object> node = config.getNode((DrasylAddress) ctx.channel().localAddress());
        if (node.containsKey("tun") && (boolean) ((Map<String, Object>) node.get("tun")).get("enabled")) {
            // create tun device
            final String name = (String) ((Map<String, Object>) node.get("tun")).get("name");
            final Subnet subnet = new Subnet((String) ((Map<String, Object>) node.get("tun")).get("subnet"));
            final InetAddress inetAddress = InetAddress.getByName((String) ((Map<String, Object>) node.get("tun")).get("address"));
            final int mtu = (int) ((Map<String, Object>) node.get("tun")).get("mtu");

            final Bootstrap b = new Bootstrap()
                    .channel(TunChannel.class)
                    .option(AUTO_READ, true)
                    .option(TUN_MTU, mtu)
                    .group(new DefaultEventLoopGroup(1))
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new TunInetAddressHandler(subnet, inetAddress, 1225));
                        }
                    });
            tunChannel = b.bind(new TunAddress(name)).syncUninterruptibly().channel();

            // create IP routing
            final Map<InetAddress, DrasylAddress> inetRoutes = new HashMap<>();
            for (ConfigValue value : list) {
                final Map<String, Object> attributes = (Map<String, Object>) value.unwrapped();
                final boolean tunRoute = (boolean) ((Map<String, Object>) attributes.get("tun")).get("route");
                if (tunRoute) {
                    final String fromAddress = (String) attributes.get("from");
                    final IdentityPublicKey fromKey = IdentityPublicKey.of(fromAddress);
                    final String toAddress = (String) attributes.get("to");
                    final IdentityPublicKey toKey = IdentityPublicKey.of(toAddress);

                    final IdentityPublicKey peerKey;
                    if (ctx.channel().localAddress().equals(fromKey)) {
                        peerKey = toKey;
                    }
                    else if (ctx.channel().localAddress().equals(toKey)) {
                        peerKey = fromKey;
                    }
                    else {
                        continue;
                    }

                    final Map<String, Object> peer = config.getNode(peerKey);
                    final InetAddress peerInetAddress = InetAddress.getByName((String) ((Map<String, Object>) peer.get("tun")).get("address"));
                    inetRoutes.put(peerInetAddress, peerKey);
                }
            }

            if (!inetRoutes.isEmpty()) {
                tunChannel.pipeline().addLast(new TunToDrasylHandler((DrasylServerChannel) ctx.channel(), inetRoutes));
            }
        }
    }

    /**
     * Assign IP address and subnet to the tun device.
     */
    public class TunInetAddressHandler extends ChannelInboundHandlerAdapter {
        private final InetAddress inetAddress;
        private final Subnet subnet;
        private final int mtu;

        public TunInetAddressHandler(final Subnet subnet,
                                     final InetAddress inetAddress,
                                     final int mtu) {
            this.subnet = requireNonNull(subnet);
            this.inetAddress = requireNonNull(inetAddress);
            this.mtu = requirePositive(mtu);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws IOException {
            ctx.fireChannelActive();

            LOG.debug("Created network device `{}`", ctx.channel().localAddress());

            // configure network device
            configurateTun((TunChannel) ctx.channel(), ctx.channel().localAddress().toString());
            LOG.debug("Assigned address `{}` with netmask `{}` to network device.", inetAddress.getHostAddress(), subnet.netmaskLength());

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

    public static class TunToDrasylHandler extends SimpleChannelInboundHandler<Tun4Packet> {
        private final DrasylServerChannel drasylServerChannel;
        private final Map<InetAddress, DrasylAddress> routes;

        public TunToDrasylHandler(final DrasylServerChannel drasylServerChannel,
                                  final Map<InetAddress, DrasylAddress> routes) {
            super(false);
            this.drasylServerChannel = requireNonNull(drasylServerChannel);
            this.routes = requireNonNull(routes);
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext tunCtx) throws Exception {
            drasylServerChannel.attr(TUN_CHANNEL_KEY).set((TunChannel) tunCtx.channel());
            for (final DrasylAddress peer : routes.values()) {
                //drasylServerChannel.serve(peer);
            }
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext tunCtx,
                                    final Tun4Packet msg) throws Exception {
            final InetAddress dst = msg.destinationAddress();
            LOG.trace("Got packet `{}`", () -> msg);
            LOG.trace("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(msg.content())));

            final DrasylAddress publicKey = routes.get(dst);
            if (routes.containsKey(dst)) {
                LOG.trace("Pass packet `{}` to peer `{}`", () -> msg, () -> publicKey);
                drasylServerChannel.serve(routes.get(dst)).addListener((GenericFutureListener<Future<DrasylChannel>>) future -> {
                    if (future.isSuccess()) {
                        final DrasylChannel channel = future.getNow();
                        channel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                                System.out.print("");
                            }
                        });
                    }
                });
            }
            else {
                LOG.trace("Drop packet `{}` with unroutable destination.", () -> msg);
                // TODO: reply with ICMP host unreachable message?
            }
        }
    }
}
