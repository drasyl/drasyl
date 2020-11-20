package org.drasyl.pipeline.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.pipeline.address.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * Handler that converts a given {@link Message} to a {@link ByteBuf}.
 */
public class Message2ByteBufHandler extends SimpleOutboundHandler<Message, Address> {
    public static final Message2ByteBufHandler INSTANCE = new Message2ByteBufHandler();
    public static final String MESSAGE_2_BYTE_BUF_HANDLER = "MESSAGE_2_BYTE_BUF_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(Message2ByteBufHandler.class);

    private Message2ByteBufHandler() {
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final Message msg,
                                final CompletableFuture<Void> future) {

        ByteBuf byteBuf = null;
        try {
            byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            final ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
            JACKSON_WRITER.writeValue((OutputStream) outputStream, msg);

            write(ctx, recipient, byteBuf, future);
        }
        catch (final IOException e) {
            byteBuf.release();
            LOG.error("Unable to serialize '{}': {}", sanitizeLogArg(msg), e.getMessage());
            future.completeExceptionally(new Exception("Message could not be serialized. This could indicate a bug in drasyl.", e));
        }
    }
}
