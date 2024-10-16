/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon;

import io.netty.channel.ChannelHandler;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.sdon.channel.SdonControllerChannelInitializer;
import org.drasyl.cli.sdon.channel.SdonControllerChildChannelInitializer;
import org.drasyl.cli.sdon.config.NetworkConfig;
import org.drasyl.identity.Identity;
import org.drasyl.util.EventLoopGroupUtil;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;

@Command(
        name = "controller",
        header = "",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class SdonControllerCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(SdonControllerCommand.class);
    @Option(
            names = { "-c", "--config" },
            description = "Loads the node configuration from specified file.",
            paramLabel = "<file>",
            defaultValue = "network.conf"
    )
    private File configFile;
    private NetworkConfig config;

    protected SdonControllerCommand() {
        super(EventLoopGroupUtil.getBestEventLoopGroup(1), EventLoopGroupUtil.getBestEventLoopGroup());
    }

    @Override
    public Integer call() {
        try {
            config = NetworkConfig.parseFile(configFile);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return super.call();
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        return new SdonControllerChannelInitializer(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled, config);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new SdonControllerChildChannelInitializer(out, err, exitCode, config);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
