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
import org.drasyl.cli.sdon.channel.SdoNodeChannelInitializer;
import org.drasyl.cli.sdon.channel.SdoNodeChildChannelInitializer;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "node",
        header = "",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class SdonNodeCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(SdonNodeCommand.class);

    @Option(
            names = { "-c", "--controller" },
            description = "Controller to connect to.",
            paramLabel = "<public-key>",
            required = true
    )
    private IdentityPublicKey controller;

    @Override
    protected ChannelHandler getServerChannelInitializer(final Worm<Integer> exitCode) {
        return new SdoNodeChannelInitializer(onlineTimeoutMillis, out, err, exitCode, controller);
    }

    @Override
    protected ChannelHandler getChildChannelInitializer(final Worm<Integer> exitCode) {
        return new SdoNodeChildChannelInitializer(out, err, exitCode, controller);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}

