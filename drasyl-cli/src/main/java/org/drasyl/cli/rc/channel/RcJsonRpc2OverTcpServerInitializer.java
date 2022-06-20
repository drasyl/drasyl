package org.drasyl.cli.rc.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.cli.rc.handler.JsonRpc2ExceptionHandler;
import org.drasyl.cli.rc.handler.JsonRpc2RequestDecoder;
import org.drasyl.cli.rc.handler.JsonRpc2ResponeEncoder;

public abstract class RcJsonRpc2OverTcpServerInitializer extends ChannelInitializer<Channel> {
    @Override
    protected void initChannel(final Channel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new JsonRpc2ResponeEncoder());
        p.addLast(new JsonRpc2RequestDecoder());
        jsonRpc2RequestStage(p);
        p.addLast(new JsonRpc2ExceptionHandler());
    }

    protected abstract void jsonRpc2RequestStage(ChannelPipeline p);
}
