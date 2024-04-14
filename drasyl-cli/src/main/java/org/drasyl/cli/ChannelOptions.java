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
package org.drasyl.cli;

import ch.qos.logback.classic.Level;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Murmur3;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.Worm;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.DrasylServerChannelConfig.ARMING_ENABLED;
import static org.drasyl.channel.DrasylServerChannelConfig.NETWORK_ID;
import static org.drasyl.channel.DrasylServerChannelConfig.SUPER_PEERS;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_BIND;
import static org.drasyl.channel.DrasylServerChannelConfig.UDP_EVENT_LOOP_SUPPLIER;
import static org.drasyl.util.Preconditions.requirePositive;
import static org.drasyl.util.network.NetworkUtil.MAX_PORT_NUMBER;

/**
 * Make sure that any {@link picocli.CommandLine.Command} inherating this class is using
 * {@link ChannelOptionsDefaultProvider} as {@code defaultValueProvider}.
 */
@SuppressWarnings("java:S118")
public abstract class ChannelOptions extends GlobalOptions implements Callable<Integer> {
    public static final short MIN_DERIVED_PORT = 22528;
    protected final PrintStream out;
    protected final PrintStream err;
    @Option(
            names = { "--identity" },
            description = "Loads the identity from specified file. If the file does not exist, a new identity will be generated an stored in this file.",
            paramLabel = "<file>",
            defaultValue = "drasyl.identity"
    )
    protected File identityFile;
    @Option(
            names = { "--bind" },
            description = "Binds UDP server to given IP and port. If no port is specified, a port in the range 22528 and 65528 is derived from the identity's public key.",
            paramLabel = "<host>[:<port>]",
            defaultValue = "0.0.0.0:0"
    )
    protected InetSocketAddress bindAddress;
    @Option(
            names = { "--network-id" },
            description = "The network this server belongs to.",
            paramLabel = "<id>",
            defaultValue = "1"
    )
    protected int networkId;
    @Option(
            names = { "--online-timeout" },
            description = "Specifies how long the server will wait for a join confirmation from at least one super peer.",
            paramLabel = "<milliseconds>",
            defaultValue = "10000"
    )
    protected int onlineTimeoutMillis;
    @Option(
            names = { "--super-peers" },
            description = "The super peers the server joins to.",
            paramLabel = "<public-key>=<host:port>",
            defaultValue = "", // Provided by ChannelOptionsDefaultProvider
            split = ","
    )
    protected Map<IdentityPublicKey, InetSocketAddress> superPeers;
    @Option(
            names = { "--no-protocol-arming" },
            description = "Disables arming (authenticating/encrypting) of all protocol messages. Ensure other nodes have arming disabled as well."
    )
    protected boolean protocolArmDisabled;
    protected EventLoopGroup childChannelLoopGroup;

    @SuppressWarnings("java:S107")
    protected ChannelOptions(final PrintStream out,
                             final PrintStream err,
                             final Level logLevel,
                             final File identityFile,
                             final InetSocketAddress bindAddress,
                             final int onlineTimeoutMillis,
                             final int networkId,
                             final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        super(logLevel);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.identityFile = identityFile;
        this.bindAddress = bindAddress;
        this.networkId = networkId;
        this.onlineTimeoutMillis = requirePositive(onlineTimeoutMillis);
        this.superPeers = superPeers;
    }

    protected ChannelOptions() {
        this.out = System.out; // NOSONAR
        this.err = System.err; // NOSONAR
    }

    @Override
    public Integer call() {
        setLogLevel();

        final EventLoop serverChannelLoop = getServerChannelLoop();
        final EventLoopGroup childChannelLoopGroup = getChildChannelLoopGroup();
        final EventLoop udpChannelLoop = getUdpChannelLoop();

        try {
            final Worm<Integer> exitCode = Worm.of();

            if (!identityFile.exists()) {
                out.println("Identity not found. Generate a new one. This may take a while...");
                IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
                out.println("Identity generated!");
            }
            final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

            if (bindAddress.getPort() == 0) {
                // derive bind port from identity
                final long identityHash = UnsignedInteger.of(Murmur3.murmur3_x86_32BytesLE(identity.getAddress().toByteArray())).getValue();
                final int identityPort = (int) (MIN_DERIVED_PORT + identityHash % (MAX_PORT_NUMBER - MIN_DERIVED_PORT));
                bindAddress = new InetSocketAddress(bindAddress.getAddress(), identityPort);
            }

            final ChannelHandler serverChannelInitializer = getServerChannelInitializer(exitCode);
            final ChannelHandler childChannelInitializer = getChildChannelInitializer(exitCode);

            final ServerBootstrap b = new ServerBootstrap()
                    .group(serverChannelLoop, childChannelLoopGroup)
                    .channel(DrasylServerChannel.class)
                    .option(NETWORK_ID, networkId)
                    .option(ARMING_ENABLED, !protocolArmDisabled)
                    .option(SUPER_PEERS, superPeers)
                    .option(UDP_BIND, bindAddress)
                    .option(UDP_EVENT_LOOP_SUPPLIER, () -> udpChannelLoop)
                    .handler(serverChannelInitializer)
                    .childHandler(childChannelInitializer);
            final Channel ch = b.bind(identity).syncUninterruptibly().channel();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log().info("Shutdown.");
                if (ch.isOpen()) {
                    ch.close().syncUninterruptibly();
                }
                serverChannelLoop.shutdownGracefully();
                childChannelLoopGroup.shutdownGracefully();
                udpChannelLoop.shutdownGracefully();
            }));

            ch.closeFuture().syncUninterruptibly();

            return exitCode.getOrSet(0);
        }
        catch (final IOException e) {
            throw new CliException("Identity could not be read from file.", e);
        }
        finally {
            serverChannelLoop.shutdownGracefully();
            childChannelLoopGroup.shutdownGracefully();
            udpChannelLoop.shutdownGracefully();
        }
    }

    protected EventLoop getServerChannelLoop() {
        return new DefaultEventLoop();
    }

    protected EventLoopGroup getChildChannelLoopGroup() {
        if (childChannelLoopGroup == null) {
            childChannelLoopGroup = EventLoopGroupUtil.getBestEventLoopGroup();
        }
        return childChannelLoopGroup;
    }

    protected EventLoop getUdpChannelLoop() {
        return getChildChannelLoopGroup().next();
    }

    protected abstract ChannelHandler getServerChannelInitializer(final Worm<Integer> exitCode);

    protected abstract ChannelHandler getChildChannelInitializer(final Worm<Integer> exitCode);
}
