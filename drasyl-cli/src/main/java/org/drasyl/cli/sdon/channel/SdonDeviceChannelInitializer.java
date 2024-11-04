/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.channel;

import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.channel.AbstractChannelInitializer;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.sdon.handler.SdonDeviceHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("java:S110")
public class SdonDeviceChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey controller;
    private final String[] tags;

    @SuppressWarnings("java:S107")
    public SdonDeviceChannelInitializer(final long onlineTimeoutMillis,
                                        final PrintStream out,
                                        final PrintStream err,
                                        final Worm<Integer> exitCode,
                                        final IdentityPublicKey controller,
                                        final String[] tags) {
        super(onlineTimeoutMillis);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.controller = requireNonNull(controller);
        this.tags = requireNonNull(tags);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        final Map<String, Object> facts = gatherFacts();

        final ChannelPipeline p = ch.pipeline();
        p.addLast(new SdonDeviceHandler(out, controller, facts));
        p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));
    }

    private Map<String, Object> gatherFacts() {
        final Map<String, Object> facts = new HashMap<>();

        facts.put("tags", tags);

        final Map<String, Object> osFacts = new HashMap<>();
        osFacts.put("arch", System.getProperty("os.arch"));
        osFacts.put("name", System.getProperty("os.name"));
        osFacts.put("version", System.getProperty("os.version"));
        facts.put("os", osFacts);

        return Map.of("sdon", facts);
    }
}
