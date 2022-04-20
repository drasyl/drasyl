package org.drasyl.jtasklet.consumer.submit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.cli.node.handler.JsonRpc2ExceptionHandler;
import org.drasyl.cli.node.handler.JsonRpc2RequestDecoder;
import org.drasyl.cli.node.handler.JsonRpc2ResponeEncoder;

import static java.util.Objects.requireNonNull;

public class SubmitJsonRpc2OverTcpServerInitializer extends ChannelInitializer<Channel> {
    private final Channel taskletChannel;

    public SubmitJsonRpc2OverTcpServerInitializer(final Channel taskletChannel) {
        this.taskletChannel = requireNonNull(taskletChannel);
    }

    @Override
    protected void initChannel(final Channel ch) {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JsonRpc2ResponeEncoder());
        p.addLast(new JsonRpc2RequestDecoder());
        p.addLast(new JsonRpc2ConsumerHandler(taskletChannel));
        p.addLast(new JsonRpc2ExceptionHandler());
    }
}
