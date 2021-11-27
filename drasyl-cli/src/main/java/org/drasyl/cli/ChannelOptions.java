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
import io.netty.channel.EventLoopGroup;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;
import org.drasyl.util.Worm;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

@SuppressWarnings("java:S118")
public abstract class ChannelOptions extends GlobalOptions implements Callable<Integer> {
    protected final PrintStream out;
    protected final PrintStream err;
    private final EventLoopGroup parentGroup;
    private final EventLoopGroup childGroup;
    @Option(
            names = { "--identity" },
            description = "Loads the identity from specified file. If the file does not exist, a new identity will be generated an stored in this file.",
            paramLabel = "<file>",
            defaultValue = "drasyl.identity.json"
    )
    protected File identityFile;
    @Option(
            names = { "--bind" },
            description = "Binds UDP server to given IP and port. If no port is specified, a random free port will be used.",
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
            names = { "--super-peer" },
            description = "The super peers the server joins to.",
            paramLabel = "<public-key=host:port>",
            defaultValue = "c0900bcfabc493d062ecd293265f571edb70b85313ba4cdda96c9f77163ba62d=sp-fra1.drasyl.org:22527,5b4578909bf0ad3565bb5faf843a9f68b325dd87451f6cb747e49d82f6ce5f4c=sp-nbg2.drasyl.org:22527",
            split = ","
    )
    protected Map<IdentityPublicKey, InetSocketAddress> superPeers;

    @SuppressWarnings("java:S107")
    protected ChannelOptions(final PrintStream out,
                             final PrintStream err,
                             final EventLoopGroup parentGroup,
                             final EventLoopGroup childGroup,
                             final Level logLevel,
                             final File identityFile,
                             final InetSocketAddress bindAddress,
                             final int onlineTimeoutMillis,
                             final int networkId,
                             final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        super(logLevel);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.parentGroup = requireNonNull(parentGroup);
        this.childGroup = requireNonNull(childGroup);
        this.identityFile = identityFile;
        this.bindAddress = bindAddress;
        this.networkId = networkId;
        this.onlineTimeoutMillis = requirePositive(onlineTimeoutMillis);
        this.superPeers = superPeers;
    }

    protected ChannelOptions(
            final EventLoopGroup parentGroup,
            final EventLoopGroup childGroup) {
        this.out = System.out; // NOSONAR
        this.err = System.err; // NOSONAR
        this.parentGroup = requireNonNull(parentGroup);
        this.childGroup = requireNonNull(childGroup);
    }

    protected ChannelOptions(final EventLoopGroup group) {
        this(group, group);
    }

    @Override
    public Integer call() {
        setLogLevel();

        try {
            final Worm<Integer> exitCode = Worm.of();

            if (!identityFile.exists()) {
                out.println("Identity not found. Generate a new one. This may take a while...");
                IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
                out.println("Identity generated!");
            }
            final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

            final ChannelHandler handler = getHandler(exitCode, identity);
            final ChannelHandler childHandler = getChildHandler(exitCode, identity);

            final ServerBootstrap b = new ServerBootstrap()
                    .group(parentGroup, childGroup)
                    .channel(DrasylServerChannel.class)
                    .handler(handler)
                    .childHandler(childHandler);
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log().info("Shutdown.");
                ch.close().syncUninterruptibly();
                parentGroup.shutdownGracefully();
                childGroup.shutdownGracefully();
            }));

            ch.closeFuture().syncUninterruptibly();

            return exitCode.getOrSet(0);
        }
        catch (final IOException e) {
            throw new CliException("Identity could not be read from file.", e);
        }
        finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }

    protected abstract ChannelHandler getHandler(final Worm<Integer> exitCode,
                                                 final Identity identity);

    protected abstract ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                                      final Identity identity);
}
