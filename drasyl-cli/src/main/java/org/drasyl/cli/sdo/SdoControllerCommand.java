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
package org.drasyl.cli.sdo;

import io.netty.channel.ChannelHandler;
import io.netty.channel.DefaultEventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.sdo.channel.SdoControllerChannelInitializer;
import org.drasyl.cli.sdo.channel.SdoControllerChildChannelInitializer;
import org.drasyl.identity.Identity;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Command(
        name = "controller",
        header = "",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class SdoControllerCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(SdoControllerCommand.class);
    @Option(
            names = { "-c", "--config" },
            description = "Loads the node configuration from specified file.",
            paramLabel = "<file>",
            defaultValue = "network.conf"
    )
    private File configFile;
    private NetworkConfig config;

    protected SdoControllerCommand() {
        super(new DefaultEventLoopGroup(1), new DefaultEventLoopGroup());
    }

    @Override
    public Integer call() {
        config = NetworkConfig.parseFile(configFile);
        return super.call();
    }

    @Override
    protected ChannelHandler getHandler(final Worm<Integer> exitCode,
                                        final Identity identity) {
        return new SdoControllerChannelInitializer(identity, udpServerGroup, bindAddress, networkId, onlineTimeoutMillis, superPeers, out, err, exitCode, !protocolArmDisabled);
    }

    @Override
    protected ChannelHandler getChildHandler(final Worm<Integer> exitCode,
                                             final Identity identity) {
        return new SdoControllerChildChannelInitializer(out, err, exitCode, config);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
