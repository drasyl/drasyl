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
package org.drasyl.cli.wormhole;

import ch.qos.logback.classic.Level;
import io.netty.channel.ChannelHandler;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.wormhole.channel.WormholeReceiveChannelInitializer;
import org.drasyl.cli.wormhole.channel.WormholeReceiveChildChannelInitializer;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Command(
        name = "receive",
        header = "Receives a text message (from \"drasyl wormhole send\")",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class WormholeReceiveCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeReceiveCommand.class);
    @Parameters(
            description = "Wormhole code given from the sender.",
            paramLabel = "<code>",
            converter = WormholeCodeConverter.class
    )
    private Pair<IdentityPublicKey, String> code;

    @SuppressWarnings("java:S107")
    WormholeReceiveCommand(final PrintStream out,
                           final PrintStream err,
                           final EventLoopGroup parentGroup,
                           final EventLoopGroup childGroup,
                           final EventLoopGroup udpServerGroup,
                           final Level logLevel,
                           final File identityFile,
                           final InetSocketAddress bindAddress,
                           final int onlineTimeoutMillis,
                           final int networkId,
                           final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                           final Pair<IdentityPublicKey, String> code) {
        super(out, err, parentGroup, childGroup, udpServerGroup, logLevel, identityFile, bindAddress, onlineTimeoutMillis, networkId, superPeers);
        this.code = requireNonNull(code);
    }

    @SuppressWarnings("unused")
    public WormholeReceiveCommand() {
        super(new DefaultEventLoopGroup(1));
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        return new WormholeReceiveChannelInitializer(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, err, exitCode, code.first(), !protocolArmDisabled);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new WormholeReceiveChildChannelInitializer(out, err, exitCode, identity, code.first(), code.second());
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
