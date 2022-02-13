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
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.perf.channel.PerfClientChannelInitializer;
import org.drasyl.cli.perf.channel.PerfClientChildChannelInitializer;
import org.drasyl.cli.perf.message.SessionRequest;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Map;

@Command(
        name = "client",
        header = "Runs in client mode, connecting to a node running in server mode"
)
public class PerfClientCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(PerfClientCommand.class);
    @Option(
            names = { "-s", "--server" },
            description = "Server to connect and send messages to.",
            paramLabel = "<public-key>",
            required = true
    )
    private IdentityPublicKey server;
    @Option(
            names = { "--direct" },
            description = "Wait for direct connection before performing the test."
    )
    private boolean waitForDirectConnection;
    @Option(
            names = { "--reverse" },
            description = "Run in reverse mode (server sends, client receives)."
    )
    private boolean reverseMode;
    @Option(
            names = { "--time" },
            description = "Time in seconds to send messages.",
            paramLabel = "<seconds>",
            defaultValue = "10"
    )
    private int testDuration;
    @Option(
            names = { "--mps" },
            description = "Messages to send per second. If this value is 0, the client will send as many messages as possible.",
            paramLabel = "<count>",
            defaultValue = "0"
    )
    private int messagesPerSecond;
    @Option(
            names = { "--size" },
            description = "Size of a single message to send.",
            paramLabel = "<bytes>",
            defaultValue = "850"
    )
    private int messageSize;

    @SuppressWarnings("java:S107")
    PerfClientCommand(final PrintStream out,
                      final PrintStream err,
                      final EventLoopGroup parentGroup,
                      final EventLoopGroup childGroup,
                      final Level logLevel,
                      final File identityFile,
                      final InetSocketAddress bindAddress,
                      final int onlineTimeoutMillis,
                      final int networkId,
                      final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                      final IdentityPublicKey server,
                      final boolean waitForDirectConnection,
                      final boolean reverseMode,
                      final int testDuration,
                      final int messagesPerSecond,
                      final int messageSize) {
        super(out, err, parentGroup, childGroup, logLevel, identityFile, bindAddress, onlineTimeoutMillis, networkId, superPeers);
        this.server = server;
        this.waitForDirectConnection = waitForDirectConnection;
        this.reverseMode = reverseMode;
        this.testDuration = testDuration;
        this.messagesPerSecond = messagesPerSecond;
        this.messageSize = messageSize;
    }

    @SuppressWarnings("unused")
    PerfClientCommand() {
        super(new NioEventLoopGroup(1));
    }

    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        final SessionRequest request = new SessionRequest(testDuration, messagesPerSecond, messageSize, reverseMode);
        return new PerfClientChildChannelInitializer(out, err, exitCode, server, waitForDirectConnection, request);
    }

    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        return new PerfClientChannelInitializer(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, err, exitCode, server, !protocolArmDisabled);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
