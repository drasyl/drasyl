package org.drasyl.pipeline.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This default codec allows to encode/decode all supported objects by Jackson.
 */
@SuppressWarnings({ "java:S110" })
public class DefaultCodec extends Codec<ObjectHolder, Object> {
    public static final DefaultCodec INSTANCE = new DefaultCodec();
    public static final String DEFAULT_CODEC = "defaultCodec";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCodec.class);

    private DefaultCodec() {
    }

    @Override
    void encode(HandlerContext ctx,
                Object msg,
                Consumer<Object> passOnConsumer) {
        if (msg instanceof byte[]) {
            // skip byte arrays
            passOnConsumer.accept(ObjectHolder.of(byte[].class, (byte[]) msg));

            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}]: Encoded Message '{}'", ctx.name(), msg);
            }
        }
        else if (ctx.validator().validate(msg.getClass()) && JSONUtil.JACKSON_WRITER.canSerialize(msg.getClass())) {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
            try (ByteBufOutputStream bos = new ByteBufOutputStream(buf)) {

                JSONUtil.JACKSON_WRITER.writeValue((OutputStream) bos, msg);

                byte[] b = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), b);

                passOnConsumer.accept(ObjectHolder.of(msg.getClass(), b));

                if (LOG.isTraceEnabled()) {
                    LOG.trace("[{}]: Encoded Message '{}'", ctx.name(), msg);
                }
            }
            catch (IOException e) {
                LOG.warn("[{}]: Unable to serialize '{}': ", ctx.name(), msg, e);
                passOnConsumer.accept(msg);
            }
            finally {
                buf.release();
            }
        }
        else {
            // can't encode, pass message to the next handler in the pipeline
            passOnConsumer.accept(msg);
        }
    }

    @Override
    void decode(HandlerContext ctx, ObjectHolder msg, Consumer<Object> passOnConsumer) {
        if (byte[].class == msg.getClazz()) {
            // skip byte arrays
            passOnConsumer.accept(msg.getObject());

            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}]: Decoded Message '{}'", ctx.name(), msg.getObject());
            }
        }
        else if (ctx.validator().validate(msg.getClazz()) && JSONUtil.JACKSON_WRITER.canSerialize(msg.getClazz())) {
            ByteBuf buf = Unpooled.wrappedBuffer(msg.getObject());
            try (ByteBufInputStream bis = new ByteBufInputStream(buf)) {

                Object decodedMessage = requireNonNull(JSONUtil.JACKSON_READER.readValue((InputStream) bis, msg.getClazz()));
                passOnConsumer.accept(decodedMessage);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("[{}]: Decoded Message '{}'", ctx.name(), decodedMessage);
                }
            }
            catch (IOException | IllegalArgumentException e) {
                LOG.warn("[{}]: Unable to deserialize '{}': ", ctx.name(), msg, e);
                passOnConsumer.accept(msg);
            }
            finally {
                buf.release();
            }
        }
        else {
            // can't decode, pass message to the next handler in the pipeline
            passOnConsumer.accept(msg);
        }
    }
}