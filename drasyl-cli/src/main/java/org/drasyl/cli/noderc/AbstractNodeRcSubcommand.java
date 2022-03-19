/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.noderc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.cli.GlobalOptions;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.cli.noderc.channel.NodeRcJsonRpc2OverTcpClientInitializer;
import org.drasyl.util.Worm;
import picocli.CommandLine.Option;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;

abstract class AbstractNodeRcSubcommand extends GlobalOptions implements Callable<Integer> {
    protected final ObjectWriter jsonWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
    @Option(
            names = { "--rc-addr" },
            description = "Connects to the remote control server at given IP and port.",
            paramLabel = "<host>:<port>",
            defaultValue = "127.0.0.1:25421"
    )
    protected InetSocketAddress rcAddress;

    @Override
    public Integer call() throws Exception {
        setLogLevel();

        final Worm<Integer> exitCode = Worm.of();
        final EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new NodeRcJsonRpc2OverTcpClientInitializer(getRequest(), response -> {
                        exitCode.trySet(onResponse(response));
                    }));
            final ChannelFuture future = b.connect(rcAddress).syncUninterruptibly();

            // wait for client to complete
            future.channel().closeFuture().syncUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }

        return exitCode.get();
    }

    protected abstract JsonRpc2Request getRequest() throws Exception;

    protected int onResponse(final JsonRpc2Response response) {
        try {
            if (response.isSuccess()) {
                final Object result = response.getResult();
                if (!(result instanceof String && ((String) result).isEmpty())) {
                    System.out.println(jsonWriter.writeValueAsString(result));
                }
                return 0;
            }
            else {
                System.out.println(jsonWriter.writeValueAsString(response.getError()));
                return response.getError().getCode();
            }
        }
        catch (final JsonProcessingException e) {
            System.err.println(e.getMessage());
            return 1;
        }
    }
}
