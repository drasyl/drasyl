package test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import static org.drasyl.util.Preconditions.requirePositive;

public class DropEveryNthInboundMessageHandler extends ChannelInboundHandlerAdapter {
    private final int n;
    private int i = 0;

    public DropEveryNthInboundMessageHandler(final int n) {
        this.n = requirePositive(n);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (!(++i % n == 0)) {
            // drop every 2nd message
            ctx.fireChannelRead(msg);
        }
    }
}
