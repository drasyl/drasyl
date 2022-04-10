package org.drasyl.jtasklet.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.discovery.PathEvent;

public class PathEventsFilter extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        // filter all path events. we don't want DrasylChannels created by these events
        if (!(evt instanceof PathEvent)) {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
