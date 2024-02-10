package org.drasyl.cli.sdo.handler.policy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.cli.sdo.config.ProactiveLatencyMeasurementsPolicy;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

public class ProactiveLatencyMeasurementsPolicyHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ProactiveLatencyMeasurementsPolicyHandler.class);
    private final ProactiveLatencyMeasurementsPolicy policy;

    public ProactiveLatencyMeasurementsPolicyHandler(final ProactiveLatencyMeasurementsPolicy policy) {
        this.policy = requireNonNull(policy);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.executor().scheduleAtFixedRate(() -> {
            for (final DrasylAddress peer : policy.peers()) {
                final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NOOP_MAGIC_NUMBER);
                LOG.error("Send NOOP to `{}`.", peer);
                final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(byteBuf, peer, (DrasylAddress) ctx.channel().localAddress());
                ctx.writeAndFlush(msg).addListener((ChannelFutureListener) channelFuture -> {
                    if (channelFuture.cause() != null) {
                        LOG.warn("Error sending NOOP: ", channelFuture.cause());
                    }
                });
                policy.setCurrentState(policy.desiredState());
            }
        }, 0, 1000, MILLISECONDS);
    }
}
