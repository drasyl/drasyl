package test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.Preconditions.requirePositive;

public class DropRandomOutboundMessagesHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DropRandomOutboundMessagesHandler.class);
    private final float lossRate;
    private int maxDrop;

    public DropRandomOutboundMessagesHandler(final float lossRate, int maxDrop) {
        this.lossRate = lossRate;
        this.maxDrop = requirePositive(maxDrop);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        // randomly drop messages
        if (maxDrop > 0 && RandomUtil.randomInt(1, 100) <= lossRate * 100) {
            LOG.info("Drop: {}", msg);
            promise.setSuccess();
            maxDrop--;
        }
        else {
            LOG.info("Pass: {}", msg);
            ctx.write(msg, promise);
        }
    }
}
