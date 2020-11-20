package org.drasyl.pipeline.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.skeletons.SimpleInboundHandler;
import org.drasyl.pipeline.address.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * Handler that converts a given {@link ByteBuf} to a {@link Message}.
 */
public class ByteBuf2MessageHandler extends SimpleInboundHandler<ByteBuf, Address> {
    public static final ByteBuf2MessageHandler INSTANCE = new ByteBuf2MessageHandler();
    public static final String BYTE_BUF_2_MESSAGE_HANDLER = "BYTE_BUF_2_MESSAGE_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(ByteBuf2MessageHandler.class);

    private ByteBuf2MessageHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ByteBuf byteBuf,
                               final CompletableFuture<Void> future) {
        try {
            final ByteBufInputStream inputStream = new ByteBufInputStream(byteBuf);
            final Message message = requireNonNull(JACKSON_READER.readValue((InputStream) inputStream, Message.class));

            ctx.fireRead(sender, message, future);
        }
        catch (final IOException e) {
            LOG.warn("Unable to deserialize '{}': {}", sanitizeLogArg(byteBuf), e.getMessage());
            future.completeExceptionally(new Exception("Message could not be deserialized.", e));
        }
        finally {
            byteBuf.release();
        }
    }
}
