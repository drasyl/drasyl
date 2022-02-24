/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.CliException;
import org.drasyl.cli.converter.SubnetConverter;
import org.drasyl.cli.tun.channel.TunChannelInitializer;
import org.drasyl.cli.tun.channel.TunChildChannelInitializer;
import org.drasyl.cli.tun.jna.AddressAndNetmaskHelper;
import org.drasyl.cli.util.InetAddressComparator;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.TunChannelOption.TUN_MTU;
import static org.drasyl.channel.tun.jna.windows.Wintun.WINTUN_ADAPTER_HANDLE;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;
import static picocli.CommandLine.Command;

@Command(
        name = "tun",
        header = "Create a local network interface routing traffic to given peers."
)
public class TunCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(TunCommand.class);
    private final Map<IdentityPublicKey, Channel> channels = new HashMap<>();
    @Option(
            names = { "--subnet" },
            description = "IP Subnet the TUN device should route traffic for. Formatted as CIDR notation.",
            converter = SubnetConverter.class,
            defaultValue = "10.225.0.0/16"
    )
    private Subnet subnet;
    @Option(
            names = { "--addr" },
            description = "IP Address assigned to the TUN device. If no address is specified, an ip address within <subnet> will be assigned (consistent for a given overlay address)."
    )
    private InetAddress address;
    @Option(
            names = { "--route" },
            description = "An overlay-to-ip-address mapping. If no address is specified, an ip address within <subnet> will be assigned (consistent for a given overlay address).",
            required = true,
            arity = "1..*",
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
            defaultValue = "1220"
    )
    private int mtu;

    protected TunCommand() {
        super(new NioEventLoopGroup(1), new NioEventLoopGroup());
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public Integer call() {
        setLogLevel();

        routes = routes.stream().map(p -> p.ensureInetAddress(subnet)).collect(Collectors.toList());

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

            final Worm<Integer> exitCode = Worm.of();

            final Bootstrap b = new Bootstrap()
                    .channel(TunChannel.class)
                    .option(AUTO_READ, true)
                    .option(TUN_MTU, mtu)
                    .group(new NioEventLoopGroup(1))
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new AddressAndSubnetHandler(identity));
                            p.addLast(new TunToDrasylHandler(identity, exitCode));
                        }
                    });
            final Channel ch = b.bind(new TunAddress(name)).syncUninterruptibly().channel();
            ch.closeFuture().syncUninterruptibly();

            return exitCode.getOrSet(0);
        }
        catch (final IOException e) {
            throw new CliException(e);
        }
        finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        // unused
        return null;
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
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

    /**
     * Assign IP address and subnet to the tun device.
     */
    private class AddressAndSubnetHandler extends ChannelInboundHandlerAdapter {
        private final Identity identity;

        public AddressAndSubnetHandler(final Identity identity) {
            this.identity = requireNonNull(identity);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws IOException {
            ctx.fireChannelActive();

            // configurate network device
            out.print("Created network device '" + ctx.channel().localAddress() + "'. Now assign address " + address.getHostAddress() + " with netmask " + subnet.netmaskLength() + " to it...");
            configurateTun((TunChannel) ctx.channel(), ctx.channel().localAddress().toString());
            out.println("done!");

            out.println("Network device is ready!");
            out.println();

            // print routing table
            out.println("My routing table:");

            final Map<InetAddress, DrasylAddress> routingTable = new HashMap<>();
            routingTable.put(address, identity.getAddress());
            routingTable.putAll(routes.stream().collect(Collectors.toMap(TunRoute::inetAddress, TunRoute::overlayAddress)));
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

            ctx.pipeline().remove(ctx.name());
        }

        @SuppressWarnings("UnstableApiUsage")
        private void configurateTun(final TunChannel channel,
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
        private Channel channel;
        private Map<InetAddress, DrasylAddress> peersMap;

        public TunToDrasylHandler(final Identity identity, final Worm<Integer> exitCode) {
            this.identity = requireNonNull(identity);
            this.exitCode = requireNonNull(exitCode);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws IOException {
            ctx.fireChannelActive();

            // create drasyl channel
            peersMap = routes.stream().collect(Collectors.toMap(TunRoute::inetAddress, TunRoute::overlayAddress));
            final Set<DrasylAddress> peersKeys = Set.copyOf(peersMap.values());
            final ChannelHandler handler = new TunChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, err, exitCode, ctx.channel(), peersKeys, !protocolArmDisabled);
            final ChannelHandler childHandler = new TunChildChannelInitializer(err, identity, ctx.channel(), peersKeys, channels);

            final ServerBootstrap b = new ServerBootstrap()
                    .group(parentGroup, childGroup)
                    .channel(DrasylServerChannel.class)
                    .handler(handler)
                    .childHandler(childHandler);
            channel = b.bind(identity.getAddress()).channel();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            channel.close();

            ctx.fireChannelInactive();
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final Tun4Packet msg) {
            final InetAddress dst = msg.destinationAddress();
            LOG.trace("Got packet `{}`", () -> msg);
            LOG.trace("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(msg.content())));

            if (address.equals(dst)) {
                // loopback
                ctx.writeAndFlush(msg.retain());
            }
            else if (peersMap.containsKey(dst) && channels.containsKey(peersMap.get(dst))) {
                LOG.trace("Pass packet `{}` to peer `{}`", () -> msg, () -> peersMap.get(dst));
                final Channel peerChannel = channels.get(peersMap.get(dst));
                peerChannel.writeAndFlush(msg.retain());
            }
            else {
                LOG.trace("Drop packet `{}` with unroutable destination.", () -> msg);
                // TODO: reply with ICMP host unreachable message?
            }
        }
    }
}
