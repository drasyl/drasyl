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
import org.drasyl.cli.tunnel.channel.TunnelConsumeChannelInitializer;
import org.drasyl.cli.tunnel.channel.TunnelConsumeChildChannelInitializer;
import org.drasyl.cli.wormhole.WormholeCodeConverter;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requireNonNegative;

@Command(
        name = "consume",
        header = "Consumes an exposed service"
)
public class TunnelConsumeCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelConsumeCommand.class);
    @Parameters(
            description = "Tunnel code given from the exposer.",
            paramLabel = "<code>",
            converter = WormholeCodeConverter.class
    )
    private Pair<IdentityPublicKey, String> code;
    @Option(
            names = { "--port" },
            description = "Local port used to expose the remote service. If no port is specified, a random free port will be used.",
            paramLabel = "<port>",
            defaultValue = "0"
    )
    private int port;

    @SuppressWarnings("java:S107")
    TunnelConsumeCommand(final PrintStream out,
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
                         final Pair<IdentityPublicKey, String> code,
                         final int port) {
        super(out, err, parentGroup, childGroup, udpServerGroup, logLevel, identityFile, bindAddress, onlineTimeoutMillis, networkId, superPeers);
        this.code = requireNonNull(code);
        this.port = requireNonNegative(port);
    }

    @SuppressWarnings("unused")
    TunnelConsumeCommand() {
        super(new DefaultEventLoopGroup(1), new DefaultEventLoopGroup());
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        return new TunnelConsumeChannelInitializer(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, err, exitCode, code.first(), !protocolArmDisabled);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new TunnelConsumeChildChannelInitializer(out, err, exitCode, identity, code.first(), code.second(), port);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
