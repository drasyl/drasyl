package org.drasyl.cli.sdo.handler.policy;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.util.concurrent.EventExecutorGroup;
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
import org.drasyl.channel.tun.jna.windows.Wintun.WINTUN_ADAPTER_HANDLE;
import org.drasyl.cli.sdo.config.TunPolicy;
import org.drasyl.cli.sdo.handler.DrasylToTunHandler;
import org.drasyl.cli.tun.jna.AddressAndNetmaskHelper;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.TunChannelOption.TUN_MTU;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;

public class TunPolicyHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TunPolicyHandler.class);
    private final TunPolicy policy;
    private Channel tunChannel;

    public TunPolicyHandler(final TunPolicy policy) {
        this.policy = requireNonNull(policy);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        final Bootstrap b = new Bootstrap()
                .channel(TunChannel.class)
                .option(AUTO_READ, true)
                .option(TUN_MTU, policy.mtu())
                .group(new DefaultEventLoopGroup(1))
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new TunToDrasylHandler((DrasylServerChannel) ctx.channel(), policy.routes()));
                    }
                });
        tunChannel = b.bind(new TunAddress(policy.name())).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                final String name = future.channel().localAddress().toString();
                final Subnet subnet = new Subnet(policy.subnet());
                final String addressStr = policy.address().getHostAddress();
                if (PlatformDependent.isOsx()) {
                    // macOS
                    exec("/sbin/ifconfig", name, "add", addressStr, addressStr);
                    exec("/sbin/ifconfig", name, "up");
                    exec("/sbin/route", "add", "-net", subnet.toString(), "-iface", name);
                } else if (PlatformDependent.isWindows()) {
                    // Windows
                    final WINTUN_ADAPTER_HANDLE adapter = ((WindowsTunDevice) ((TunChannel) future.channel()).device()).adapter();

                    final Pointer interfaceLuid = new Memory(8);
                    WintunGetAdapterLUID(adapter, interfaceLuid);
                    AddressAndNetmaskHelper.setIPv4AndNetmask(interfaceLuid, addressStr, subnet.netmaskLength());

                    exec("netsh", "interface", "ipv4", "set", "subinterface", name, "mtu=" + policy.mtu(), "store=active");
                } else {
                    // Linux
                    exec("/sbin/ip", "addr", "add", addressStr + "/" + subnet.netmaskLength(), "dev", name);
                    exec("/sbin/ip", "link", "set", "dev", name, "up");
                }
            }
        }).syncUninterruptibly().channel();

    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (tunChannel != null) {
            tunChannel.close();
        }
    }

    private void exec(final String... command) throws IOException {
        try {
            LOG.trace("Execute: {}", String.join(" ", command));
            final int exitCode = Runtime.getRuntime().exec(command).waitFor();
            if (exitCode != 0) {
                throw new IOException("Executing `" + String.join(" ", command) + "` returned non-zero exit code (" + exitCode + ").");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class TunToDrasylHandler extends SimpleChannelInboundHandler<Tun4Packet> {
        private final DrasylServerChannel drasylServerChannel;
        private final Map<InetAddress, DrasylAddress> routes;

        public TunToDrasylHandler(final DrasylServerChannel drasylServerChannel,
                                  final Map<InetAddress, DrasylAddress> routes) {
            super(false);
            this.drasylServerChannel = requireNonNull(drasylServerChannel);
            this.routes = requireNonNull(routes);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final Tun4Packet msg) {
            final InetAddress dst = msg.destinationAddress();
            LOG.error("Got packet `{}`", () -> msg);
            LOG.error("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(msg.content())));

            final DrasylAddress publicKey = routes.get(dst);
            if (routes.containsKey(dst)) {
                LOG.error("Pass packet `{}` to peer `{}`", () -> msg, () -> publicKey);
                drasylServerChannel.serve(routes.get(dst)).addListener((GenericFutureListener<Future<DrasylChannel>>) future -> {
                    if (future.isSuccess()) {
                        LOG.error("Pass packet `{}` to peer `{}`!!!", () -> msg, () -> publicKey);
                        final DrasylChannel channel = future.getNow();
                        channel.writeAndFlush(msg).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    }
                });
            } else {
                LOG.error("Drop packet `{}` with unroutable destination.", () -> msg);
                // TODO: reply with ICMP host unreachable message?
            }
        }
    }

    public static class DrasylToTunHandler extends SimpleChannelInboundHandler<Tun4Packet> {
        public DrasylToTunHandler() {
            super(false);
        }

        @SuppressWarnings("java:S1905")
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final Tun4Packet packet) {
            LOG.error("channelRead0");
            final DrasylServerChannel parent = (DrasylServerChannel) ctx.channel().parent();
            final ChannelHandlerContext tunPolicyHandlerCtx = parent.pipeline().context(TunPolicy.HANDLER_NAME);
            if (tunPolicyHandlerCtx != null) {
                LOG.error("Got `{}` from drasyl `{}`", packet, ctx.channel().remoteAddress());
                final TunPolicyHandler tunPolicyHandler = (TunPolicyHandler) tunPolicyHandlerCtx.handler();
                if (tunPolicyHandler.tunChannel != null) {
                    tunPolicyHandler.tunChannel.writeAndFlush(packet).addListener(FIRE_EXCEPTION_ON_FAILURE);
                }
            }
        }
    }
}
