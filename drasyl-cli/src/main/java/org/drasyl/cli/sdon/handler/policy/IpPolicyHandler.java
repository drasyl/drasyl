/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

package org.drasyl.cli.sdon.handler.policy;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.TunAddress;
import org.drasyl.channel.tun.TunChannel;
import org.drasyl.channel.tun.jna.windows.WindowsTunDevice;
import org.drasyl.channel.tun.jna.windows.Wintun.WINTUN_ADAPTER_HANDLE;
import org.drasyl.cli.sdon.config.TunPolicy;
import org.drasyl.cli.tun.jna.AddressAndNetmaskHelper;
import org.drasyl.crypto.HexUtil;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.Subnet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.tun.TunChannelOption.TUN_MTU;
import static org.drasyl.channel.tun.jna.windows.Wintun.WintunGetAdapterLUID;
import static org.drasyl.cli.sdon.config.Policy.PolicyState.ABSENT;
import static org.drasyl.cli.sdon.config.TunPolicy.TUN_CHANNEL_KEY;
import static org.drasyl.cli.tun.handler.TunPacketCodec.MAGIC_NUMBER;

public class IpPolicyHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(IpPolicyHandler.class);
    private final TunPolicy policy;
    private Channel tunChannel;

    public IpPolicyHandler(final TunPolicy policy) {
        this.policy = requireNonNull(policy);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        final Bootstrap b = new Bootstrap()
                .channel(TunChannel.class)
                .option(AUTO_READ, true)
                .option(TUN_MTU, 1500)
                .group(new DefaultEventLoopGroup(1))
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();

                        final DrasylServerChannel parent = (DrasylServerChannel) ctx.channel();
                        parent.attr(TUN_CHANNEL_KEY).set((TunChannel) ch);

                        p.addLast(new TunToDrasylHandler(parent, policy));
                    }
                });
        tunChannel = b.bind(new TunAddress()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                final String name = future.channel().localAddress().toString();
                final String addressStr = policy.address().getHostAddress();
                final Subnet subnet = new Subnet(addressStr + "/" + policy.netmask());
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

//                    exec("netsh", "interface", "ipv4", "set", "subinterface", name, "mtu=" + policy.mtu(), "store=active");
                }
                else {
                    // Linux
                    exec("/sbin/ip", "addr", "add", addressStr + "/" + subnet.netmaskLength(), "dev", name);
                    exec("/sbin/ip", "link", "set", "dev", name, "up");
                }

                policy.setCurrentState(policy.desiredState());
            }
        }).syncUninterruptibly().channel();

    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (tunChannel != null) {
            final SocketAddress ifName = tunChannel.localAddress();
            tunChannel.close().syncUninterruptibly().addListener(FIRE_EXCEPTION_ON_FAILURE);
            // FIXME: for some reason the interface is not removed properly. Should be fixed. Here's my workaround
            try {
                exec("/usr/sbin/ip", "link", "delete", ifName.toString());
            }
            catch (final IOException e) {
                // ignore
            }
            policy.setCurrentState(ABSENT);
        }
    }

    private void exec(final String... command) throws IOException {
        try {
            LOG.trace("Execute: {}", String.join(" ", command));
            final Process process = Runtime.getRuntime().exec(command);
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Get the stderr output
                final BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                final StringBuilder errorOutput = new StringBuilder();

                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }

                // Print or handle the stderr output
                System.out.println("Stderr Output: " + errorOutput);

                throw new IOException("Executing `" + String.join(" ", command) + "` returned non-zero exit code (" + exitCode + ").");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class TunToDrasylHandler extends SimpleChannelInboundHandler<Tun4Packet> {
        private final DrasylServerChannel parent;
        private final TunPolicy policy;

        public TunToDrasylHandler(final DrasylServerChannel parent,
                                  final TunPolicy policy) {
            super(false);
            this.parent = requireNonNull(parent);
            this.policy = requireNonNull(policy);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final Tun4Packet packet) {
            final InetAddress dst = packet.destinationAddress();
            LOG.debug("Got packet from TUN interface `{}`.", () -> packet);
            LOG.trace("https://hpd.gasmi.net/?data={}&force=ipv4", () -> HexUtil.bytesToHex(ByteBufUtil.getBytes(packet.content())));

            // mapping
            final Map<InetAddress, DrasylAddress> mapping = policy.mapping();
            final DrasylAddress drasylAddress = mapping.get(dst);

            if (drasylAddress != null) {
                LOG.debug("Write to `{}`", () -> drasylAddress);

                // resolve endpoint
                final PeersManager peersManager = parent.config().getPeersManager();
                final InetSocketAddress endpoint = peersManager.resolve(drasylAddress);

                // build message
                final ByteBuf byteBuf = ctx.alloc().compositeBuffer(2)
                        .addComponent(true, ctx.alloc().buffer(4).writeInt(MAGIC_NUMBER))
                        .addComponent(true, packet.content().retain());
                final ApplicationMessage appMsg = ApplicationMessage.of(parent.config().getNetworkId(), (IdentityPublicKey) drasylAddress, parent.identity().getIdentityPublicKey(), parent.identity().getProofOfWork(), byteBuf);
                final InetAddressedMessage<ApplicationMessage> inetMsg = new InetAddressedMessage<>(appMsg, endpoint);

                // send message
                parent.udpChannel().writeAndFlush(inetMsg).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
            else {
                LOG.info("Drop packet `{}` with unroutable destination.", () -> packet);
                packet.release();
            }
        }
    }
}
