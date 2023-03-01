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
package org.drasyl.jtasklet.consumer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import org.drasyl.cli.GlobalOptions;
import org.drasyl.jtasklet.consumer.channel.rc.OffloadRcJsonRpcOverTcpServerInitializer;
import org.drasyl.node.DrasylNodeSharedEventLoopGroupHolder;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

import static org.drasyl.util.EventLoopGroupUtil.getBestServerSocketChannel;

@Command(
        name = "offload-rc",
        header = { "Run a node in JsonRPC mode." },
        showDefaultValues = true
)
public class OffloadRcJsonRpcCommand extends GlobalOptions implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(OffloadRcJsonRpcCommand.class);
    @Option(
            names = { "--bind" },
            description = "Binds remote control server to given IP and port. If no port is specified, a random free port will be used.",
            paramLabel = "<host>[:<port>]",
            defaultValue = "0.0.0.0:25421"
    )
    protected InetSocketAddress rcBindAddress;

    @Override
    public Integer call() {
        setLogLevel();

        final OffloadRcJsonRpcOverTcpServerInitializer channelInitializer = new OffloadRcJsonRpcOverTcpServerInitializer();
        final ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(DrasylNodeSharedEventLoopGroupHolder.getParentGroup(), DrasylNodeSharedEventLoopGroupHolder.getChildGroup())
                .channel(getBestServerSocketChannel())
                .childHandler(channelInitializer);
        final Channel rcChannel = serverBootstrap.bind(rcBindAddress).syncUninterruptibly().channel();

        LOG.info("Started remote control server listening on tcp:/{}", rcChannel.localAddress());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (rcChannel.isOpen()) {
                LOG.info("Shutdown remote control server.");
                rcChannel.close().syncUninterruptibly();
            }
        }));

        rcChannel.closeFuture().syncUninterruptibly();

        return 0;
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
