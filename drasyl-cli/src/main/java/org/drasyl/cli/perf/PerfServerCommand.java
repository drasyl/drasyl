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
package org.drasyl.cli.perf;

import ch.qos.logback.classic.Level;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.perf.channel.PerfServerChannelInitializer;
import org.drasyl.cli.perf.channel.PerfServerChildChannelInitializer;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

@Command(
        name = "server",
        header = "Runs in server mode, waiting for connections from client nodes",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class PerfServerCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(PerfServerCommand.class);

    @SuppressWarnings("java:S107")
    PerfServerCommand(final PrintStream out,
                      final PrintStream err,
                      final Level logLevel,
                      final File identityFile,
                      final InetSocketAddress bindAddress,
                      final int onlineTimeoutMillis,
                      final int networkId,
                      final Map<IdentityPublicKey, InetSocketAddress> superPeers) {
        super(out, err, logLevel, identityFile, bindAddress, onlineTimeoutMillis, networkId, superPeers);
    }

    @SuppressWarnings("unused")
    PerfServerCommand() {
    }

    @Override
    protected ChannelHandler getServerChannelInitializer(final Worm<Integer> exitCode,
                                                         final Identity identity,
                                                         final EventLoopGroup udpChannelLoop) {
        return new PerfServerChannelInitializer(identity, udpChannelLoop, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled);
    }

    @Override
    protected ChannelHandler getChildChannelInitializer(final Worm<Integer> exitCode,
                                                        final Identity identity) {
        return new PerfServerChildChannelInitializer(out, err, exitCode);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
