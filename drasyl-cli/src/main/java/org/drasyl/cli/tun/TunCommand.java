/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.tun;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.CliException;
import org.drasyl.cli.converter.SubnetConverter;
import org.drasyl.cli.tun.channel.TunChannelInitializer;
import org.drasyl.cli.tun.channel.TunChildChannelInitializer;
import org.drasyl.cli.tun.channel.TunRcJsonRpc2OverHttpServerInitializer;
import org.drasyl.cli.tun.channel.TunRcJsonRpc2OverTcpServerInitializer;
import org.drasyl.cli.tun.jna.AddressAndNetmaskHelper;
import org.drasyl.cli.util.InetAddressComparator;
import org.drasyl.crypto.HexUtil;
import org.drasyl.handler.remote.UdpServerChannelInitializer;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.DrasylNodeSharedEventLoopGroupHolder;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.SO_BROADCAST;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.NETWORK_ID;
import static org.drasyl.channel.DrasylServerChannelConfig.SUPER_PEERS;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BIND;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BOOTSTRAP;
import static org.drasyl.channel.tun.TunChannelOption.TUN_MTU;
import static org.drasyl.channel.tun.jna.windows.Wintun.WINTUN_ADAPTER_HANDLE;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;
import static picocli.CommandLine.Command;

@Command(
        name = "tun",
        header = "Create a local network interface routing traffic to given peers.",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class TunCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(TunCommand.class);
    private final Set<Channel> channelsToFlush = new HashSet<>();
    @Option(
            names = { "--rc-bind" },
            description = "Binds remote control server to given IP and port. If no port is specified, a random free port will be used.",
            paramLabel = "<host>[:<port>]",
            defaultValue = "0.0.0.0:25421"
    )
    protected InetSocketAddress rcBindAddress;
    @Option(
            names = { "--subnet" },
            description = "IP Subnet the TUN device should route traffic for. Formatted as CIDR notation.",
            converter = SubnetConverter.class,
            defaultValue = "10.225.0.0/16"
    )
    private Subnet subnet;
    @Option(
            names = { "--addr" },
            description = {
                    "IP Address assigned to the TUN device.",
                    "If no address is specified, an ip address within <subnet> will be assigned (consistent for a given overlay address)."
            }
    )
    private InetAddress address;
    @Option(
            names = { "--route" },
            description = {
                    "An overlay-to-ip-address mapping.",
                    "If no address is specified, an ip address within <subnet> will be assigned (consistent for a given overlay address)."
            },
            converter = TunRouteConverter.class,
            paramLabel = "<public-key>[=<address>]"
    )
    private List<TunRoute> routes;
    @Option(
            names = { "--name" },
            description = {
                    "Name of the tun device.",
                    "On Linux: Must be shorter than 16 characters.",
                    "On macOS: Must be 'utun' followed by a number (e.g., tun0)."
            },
            defaultValue = "utun0"
    )
    private String name;
    @Option(
            names = { "--mtu" },
            description = {
                    "MTU of the tun device.",
                    "Not supported on windows. You can manually adjust the MTU size by running command 'netsh interface ipv4 set subinterface <name> mtu=<mtu> store=active'."
            },
            defaultValue = "1225"
    )
    private int mtu;
    @Option(
            names = { "--no-application-arming" },
            description = "Disables arming (authenticating/encrypting) of all application messages. Ensure other nodes have arming disabled as well."
    )
    protected boolean applicationArmDisabled;
    @ArgGroup
    private RemoteControl rc;

    protected TunCommand() {
    }

    @Override
    public Integer call() {
        setLogLevel();

        if (routes != null) {
            routes = routes.stream().map(p -> p.ensureInetAddress(subnet)).collect(Collectors.toList());
        }
        else {
            routes = new ArrayList<>();
        }

        final EventLoop serverChannelLoop = getServerChannelLoop();
        final EventLoopGroup childChannelLoopGroup = getChildChannelLoopGroup();
        final EventLoop udpChannelLoop = getUdpChannelLoop();

        Channel rcChannel = null;
        try {
            if (!identityFile.exists()) {
                out.println("Identity not found. Generate a new one. This may take a while...");
                IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
                out.println("Identity generated!");
            }
            final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

            if (address == null) {
                address = TunRoute.deriveInetAddressFromOverlayAddress(subnet, identity.getAddress());
            }

            if (!subnet.contains(address)) {
                throw new IllegalStateException("Given TUN device address must be part of the given subnet.");
            }

            final Map<InetAddress, DrasylAddress> routesMap = routes.stream().collect(Collectors.toMap(TunRoute::inetAddress, TunRoute::overlayAddress));
            final Worm<Integer> exitCode = Worm.of();

            final Bootstrap b = new Bootstrap()
                    .channel(TunChannel.class)
                    .option(AUTO_READ, true)
                    .option(TUN_MTU, mtu)
                    .group(new DefaultEventLoopGroup(1))
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new AddressAndSubnetHandler(identity, routesMap));
                            p.addLast(new TunToDrasylHandler(identity, exitCode, routesMap, serverChannelLoop, childChannelLoopGroup, udpChannelLoop));
                        }
                    });
            final Channel ch = b.bind(new TunAddress(name)).syncUninterruptibly().channel();

            if (rc != null) {
                final TunRcJsonRpc2OverTcpServerInitializer channelInitializer;
                if (rc.rcTcpJsonRpc) {
                    channelInitializer = new TunRcJsonRpc2OverTcpServerInitializer(routesMap, identity, subnet, ch, address);
                }
                else {
                    channelInitializer = new TunRcJsonRpc2OverHttpServerInitializer(routesMap, identity, subnet, ch, address);
                }
                final ServerBootstrap rcBootstrap = new ServerBootstrap()
                        .group(DrasylNodeSharedEventLoopGroupHolder.getParentGroup(), DrasylNodeSharedEventLoopGroupHolder.getChildGroup())
                        .channel(NioServerSocketChannel.class)
                        .childHandler(channelInitializer);
                rcChannel = rcBootstrap.bind(rcBindAddress).syncUninterruptibly().channel();
                LOG.info("Started remote control server listening on tcp:/{}", rcChannel.localAddress());
            }

            ch.closeFuture().syncUninterruptibly();

            return exitCode.getOrSet(0);
        }
        catch (final IOException e) {
            throw new CliException(e);
        }
        finally {
            if (rcChannel != null) {
                rcChannel.close().syncUninterruptibly();
            }
            serverChannelLoop.shutdownGracefully();
            childChannelLoopGroup.shutdownGracefully();
        }
    }

    @Override
    protected ChannelHandler getServerChannelInitializer(final Worm<Integer> exitCode) {
        // unused
        return null;
    }

    @Override
    protected ChannelHandler getChildChannelInitializer(final Worm<Integer> exitCode) {
        // unused
        return null;
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    private static void exec(final String... command) throws IOException {
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

    public static void printRoutingTable(final PrintStream out,
                                         final Identity identity,
                                         final InetAddress address,
                                         final Map<InetAddress, DrasylAddress> routes) {
        out.println("My routing table:");

        final Map<InetAddress, DrasylAddress> routingTable = new HashMap<>(routes);
        routingTable.put(address, identity.getAddress());
        final List<InetAddress> inetAddresses = new ArrayList<>(routingTable.keySet());
        inetAddresses.sort(new InetAddressComparator());

        for (final InetAddress inetAddress : inetAddresses) {
            out.print("  ");
            out.printf("%1$-14s", inetAddress.getHostAddress());
            out.print(" <-> ");
            out.print(routingTable.get(inetAddress));
            if (address.equals(inetAddress)) {
                out.print(" (this is me)");
            }
            out.println();
        }
        out.println();
    }

    /**
     * Assign IP address and subnet to the tun device.
     */
    private class AddressAndSubnetHandler extends ChannelInboundHandlerAdapter {
        private final Identity identity;
        private final Map<InetAddress, DrasylAddress> routes;

        public AddressAndSubnetHandler(final Identity identity,
                                       final Map<InetAddress, DrasylAddress> routes) {
            this.identity = requireNonNull(identity);
            this.routes = requireNonNull(routes);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws IOException {
            ctx.fireChannelActive();

            // configure network device
            out.print("Created network device '" + ctx.channel().localAddress() + "'. Now assign address " + address.getHostAddress() + " with netmask " + subnet.netmaskLength() + " to it...");
            configureTun((TunChannel) ctx.channel(), ctx.channel().localAddress().toString());
            out.println("done!");

            out.println("Network device is ready!");
            out.println();

            // print routing table
            printRoutingTable(out, identity, address, routes);

            ctx.pipeline().remove(ctx.name());
        }

        private void configureTun(final TunChannel channel,
                                  final String name) throws IOException {
            final String addressStr = address.getHostAddress();
            if (PlatformDependent.isOsx()) {
                // macOS
                exec("/sbin/ifconfig", name, "add", addressStr, addressStr);
                exec("/sbin/ifconfig", name, "up");
                exec("/sbin/route", "add", "-net", subnet.toString(), "-iface", name);
            }
            else if (PlatformDependent.isWindows()) {
                // Windows
                final WINTUN_ADAPTER_HANDLE adapter = ((WindowsTunDevice) channel.device()).adapter();

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
    }

    /**
     * Routes inbound messages to the given drasyl-based channel.
     * <p>
     * This handler has to be placed in a {@link TunChannel}.
     */
    private class TunToDrasylHandler extends SimpleChannelInboundHandler<Tun4Packet> {
        private final Identity identity;
        private final Worm<Integer> exitCode;
        private DrasylServerChannel channel;
        private final Map<InetAddress, DrasylAddress> routes;
        private final EventLoop serverChannelLoop;
        private final EventLoopGroup childChannelLoopGroup;
        private final EventLoop udpChannelLoop;

        public TunToDrasylHandler(final Identity identity,
                                  final Worm<Integer> exitCode,
                                  final Map<InetAddress, DrasylAddress> routes,
                                  final EventLoop serverChannelLoop,
                                  final EventLoopGroup childChannelLoopGroup,
                                  final EventLoop udpChannelLoop) {
            this.identity = requireNonNull(identity);
            this.exitCode = requireNonNull(exitCode);
            this.routes = requireNonNull(routes);
            this.serverChannelLoop = requireNonNull(serverChannelLoop);
            this.childChannelLoopGroup = requireNonNull(childChannelLoopGroup);
            this.udpChannelLoop = requireNonNull(udpChannelLoop);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            ctx.fireChannelActive();

            // create drasyl channel
            final ChannelHandler handler = new TunChannelInitializer(onlineTimeoutMillis, err, exitCode, ctx.channel(), new HashSet<>(routes.values()));
            final ChannelHandler childHandler = new TunChildChannelInitializer(err, ctx.channel(), routes, !applicationArmDisabled);

            final ServerBootstrap b = new ServerBootstrap()
                    .group(serverChannelLoop, childChannelLoopGroup)
                    .channel(DrasylServerChannel.class)
                    .option(NETWORK_ID, networkId)
                    .option(ARMING_ENABLED, !protocolArmDisabled)
                    .option(SUPER_PEERS, superPeers)
                    .option(UDP_BIND, bindAddress)
                    .option(UDP_BOOTSTRAP, parent -> new Bootstrap()
                            .option(SO_BROADCAST, false)
                            .option(IP_TOS, 0xB8)
                            .group(udpChannelLoop)
                            .channel(EventLoopGroupUtil.getBestDatagramChannel())
                            .handler(new UdpServerChannelInitializer(parent))
                    )
                    .handler(handler)
                    .childHandler(childHandler);
            channel = (DrasylServerChannel) b.bind(identity).channel();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            channel.close();

            ctx.fireChannelInactive();
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final Tun4Packet msg) {
            final InetAddress dst = msg.destinationAddress();
            LOG.debug("Got packet `{}` from TUN interface.", () -> msg);
            LOG.debug("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(msg.content())));

            if (address.equals(dst)) {
                // loopback
                ctx.writeAndFlush(msg.retain()).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
            else {
                final DrasylAddress publicKey = routes.get(dst);
                if (routes.containsKey(dst)) {
                    LOG.debug("Pass packet `{}` to peer `{}` via drasyl network", () -> msg, () -> publicKey);
                    msg.retain();
                    channel.serve(publicKey).addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            final Channel peerChannel = future.channel();
                            peerChannel.closeFuture().addListener(future2 -> channelsToFlush.remove(peerChannel));
                            peerChannel.writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE);
                        }
                        else {
                            msg.release();
                        }
                    });
                }
                else {
                    LOG.debug("Drop packet `{}` from TUN interface with unroutable destination.", () -> msg);
                    // TODO: reply with ICMP host unreachable message?
                }
            }
        }

        @Override
        public void channelReadComplete(final ChannelHandlerContext ctx) {
            ctx.flush();
//            for (Channel peerChannel : channelsToFlush) {
//                peerChannel.flush();
//            }
            channelsToFlush.clear();

            ctx.fireChannelReadComplete();
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
            if (evt instanceof AddRoute) {
                // ensure channel exists
                channel.serve(((AddRoute) evt).publicKey);
            }
            else if (evt instanceof RemoveRoute) {
                channel.serve(((RemoveRoute) evt).publicKey).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        future.channel().close();
                    }
                });
            }
            else {
                ctx.fireUserEventTriggered(evt);
            }
        }
    }

    static class RemoteControl {
        @Option(
                names = "--rc-jsonrpc-tcp",
                description = {
                        "Starts a JSON-RPC 2.0 over TCP server listening on remote requests.",
                        "Available methods: routes, add-route, remove-route, identity"
                }
        )
        boolean rcTcpJsonRpc;
        @Option(
                names = "--rc-jsonrpc-http",
                description = {
                        "Starts a JSON-RPC 2.0 over HTTP server listening on remote requests.",
                        "Available methods: routes, add-route, remove-route, identity"
                }
        )
        boolean rcTcpJsonHttp;
    }

    public static class AddRoute {
        final IdentityPublicKey publicKey;

        public AddRoute(final IdentityPublicKey publicKey) {
            this.publicKey = requireNonNull(publicKey);
        }
    }

    public static class RemoveRoute {
        final IdentityPublicKey publicKey;

        public RemoveRoute(final IdentityPublicKey publicKey) {
            this.publicKey = requireNonNull(publicKey);
        }
    }
}
