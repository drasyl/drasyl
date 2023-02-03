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
package org.drasyl.cli.tunnel;

import ch.qos.logback.classic.Level;
import io.netty.channel.ChannelHandler;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.tunnel.channel.TunnelExposeChannelInitializer;
import org.drasyl.cli.tunnel.channel.TunnelExposeChildChannelInitializer;
import org.drasyl.crypto.Crypto;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Command(
        name = "expose",
        header = "Exposes a (local) service",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class TunnelExposeCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelExposeCommand.class);
    public static final int PASSWORD_LENGTH = 16;
    public static final int WRITE_TIMEOUT_SECONDS = 15;
    @Option(
            names = { "--password" },
            description = "Password required by the consumer. If no password is specified, a random password will be generated.",
            interactive = true
    )
    String password;
    @ArgGroup(multiplicity = "1")
    Service service;

    @SuppressWarnings("java:S107")
    TunnelExposeCommand(final PrintStream out,
                        final PrintStream err,
                        final EventLoopGroup parentGroup,
                        final EventLoopGroup childGroup,
                        final NioEventLoopGroup udpServerGroup,
                        final Level logLevel,
                        final File identityFile,
                        final InetSocketAddress bindAddress,
                        final int onlineTimeoutMillis,
                        final int networkId,
                        final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                        final String password,
                        final Service service) {
        super(out, err, parentGroup, childGroup, udpServerGroup, logLevel, identityFile, bindAddress, onlineTimeoutMillis, networkId, superPeers);
        this.password = requireNonNull(password);
        this.service = requireNonNull(service);
    }

    @SuppressWarnings("unused")
    TunnelExposeCommand() {
        super(new DefaultEventLoopGroup(1), new DefaultEventLoopGroup());
    }

    @Override
    public Integer call() {
        if (password == null || password.isEmpty()) {
            password = Crypto.randomString(PASSWORD_LENGTH);
        }

        return super.call();
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        return new TunnelExposeChannelInitializer(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, service, password, out, err, exitCode, !protocolArmDisabled);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new TunnelExposeChildChannelInitializer(err, exitCode, identity, password, service);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    public static class Service {
        @Option(
                names = { "--tcp" },
                description = "(Local) TCP service to expose. When no <host> is given, 127.0.0.1 will be used.",
                paramLabel = "[<host>:]<port>",
                converter = TunnelServiceConverter.class
        )
        private InetSocketAddress tcp;

        Service(final InetSocketAddress tcp) {
            this.tcp = tcp;
        }

        public Service() {
        }

        public InetSocketAddress getTcp() {
            return tcp;
        }
    }
}
