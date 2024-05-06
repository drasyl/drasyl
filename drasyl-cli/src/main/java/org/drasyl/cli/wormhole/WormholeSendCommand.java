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
import io.netty.channel.EventLoopGroup;
import org.drasyl.cli.ChannelOptions;
import org.drasyl.cli.ChannelOptionsDefaultProvider;
import org.drasyl.cli.wormhole.channel.WormholeSendChannelInitializer;
import org.drasyl.cli.wormhole.channel.WormholeSendChildChannelInitializer;
import org.drasyl.crypto.Crypto;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.EventLoopGroupUtil;
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
        name = "send",
        header = "Sends a text message or file",
        defaultValueProvider = ChannelOptionsDefaultProvider.class
)
public class WormholeSendCommand extends ChannelOptions {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeSendCommand.class);
    public static final int PASSWORD_LENGTH = 16;
    @Option(
            names = { "--password" },
            description = "Password required by the binder. If no password is specified, a random password will be generated.",
            interactive = true
    )
    private String password;
    @ArgGroup(multiplicity = "1")
    private Payload payload;

    @SuppressWarnings("java:S107")
    WormholeSendCommand(final PrintStream out,
                        final PrintStream err,
                        final Level logLevel,
                        final File identityFile,
                        final InetSocketAddress bindAddress,
                        final int onlineTimeoutMillis,
                        final int networkId,
                        final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                        final String password,
                        final Payload payload) {
        super(out, err, logLevel, identityFile, bindAddress, onlineTimeoutMillis, networkId, superPeers);
        this.password = requireNonNull(password);
        this.payload = requireNonNull(payload);
    }

    @SuppressWarnings("unused")
    public WormholeSendCommand() {
    }

    @Override
    public Integer call() {
        if (password == null || password.isEmpty()) {
            password = Crypto.randomString(PASSWORD_LENGTH);
        }

        return super.call();
    }

    @Override
    protected EventLoopGroup getChildChannelLoopGroup() {
        // we have only one peer
        if (childChannelLoopGroup == null) {
            childChannelLoopGroup = EventLoopGroupUtil.getBestEventLoopGroup(1);
        }
        return childChannelLoopGroup;
    }

    @Override
    protected ChannelHandler getServerChannelInitializer(final Worm<Integer> exitCode) {
        return new WormholeSendChannelInitializer(onlineTimeoutMillis, out, err, exitCode, password);
    }

    @Override
    protected ChannelHandler getChildChannelInitializer(final Worm<Integer> exitCode) {
        return new WormholeSendChildChannelInitializer(out, err, exitCode, password, payload);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    public static class Payload {
        @Option(
                names = { "--text" },
                description = "Text message to send.",
                paramLabel = "<message>",
                required = true
        )
        private String text;
        @Option(
                names = { "--file" },
                description = "File to send.",
                paramLabel = "<file>",
                required = true
        )
        private File file;

        Payload(final String text, final File file) {
            if (text == null && file == null) {
                throw new IllegalArgumentException("text and file");
            }
            this.text = text;
            this.file = file;
        }

        public Payload() {
        }

        public String getText() {
            return text;
        }

        public File getFile() {
            return file;
        }
    }
}
