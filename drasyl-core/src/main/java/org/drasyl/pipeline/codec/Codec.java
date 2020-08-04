package org.drasyl.pipeline.codec;

import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleDuplexHandler;
import org.drasyl.util.FutureUtil;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Standard interface for all codecs of the {@link org.drasyl.pipeline.Pipeline}.
 * <br>
 * A codec can be used to encode/decode a given set of objects into the correct format to process
 * the object in the ongoing steps.
 * <br>
 * A codec must have a symmetrical construction. {@link #encode(HandlerContext, Object, Consumer)}
 * converts an object of type D into type E and {@link #decode(HandlerContext, Object, Consumer)}
 * vice versa.
 *
 * <p>
 * <b>Note</b>: You can use the {@link HandlerContext#validator()} to check if a given {@code
 * Class} is allowed to be encode/decode.
 * </p>
 */
public abstract class Codec<E, D> extends SimpleDuplexHandler<E, Event, D> {
    @Override
    protected void matchedEventTriggered(HandlerContext ctx,
                                         Event event,
                                         CompletableFuture<Void> future) {
        // Skip
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(HandlerContext ctx,
                               CompressedPublicKey sender,
                               E msg,
                               CompletableFuture<Void> future) {
        if (future.isDone()) {
            ctx.fireRead(sender, msg, future);
            return;
        }

        ArrayList<CompletableFuture<?>> futures = new ArrayList<>();

        decode(ctx, msg, decodedMessage -> {
            CompletableFuture<Void> dependingFuture = new CompletableFuture<>();
            futures.add(dependingFuture);

            ctx.fireRead(sender, decodedMessage, dependingFuture);
        });

        FutureUtil.completeOnAllOf(future, futures);
    }

    @Override
    protected void matchedWrite(HandlerContext ctx,
                                CompressedPublicKey recipient,
                                D msg,
                                CompletableFuture<Void> future) {
        if (future.isDone()) {
            ctx.write(recipient, msg, future);
            return;
        }

        ArrayList<CompletableFuture<?>> futures = new ArrayList<>();

        encode(ctx, msg, encodedMessage -> {
            CompletableFuture<Void> dependingFuture = new CompletableFuture<>();
            futures.add(dependingFuture);

            ctx.write(recipient, encodedMessage, dependingFuture);
        });

        FutureUtil.completeOnAllOf(future, futures);
    }

    /**
     * Encodes a given object of type {@code D} into type {@code E}.
     *
     * <br>
     * You have to use the given {@code passOnConsumer} to pass all objects to the next handler in
     * the pipeline, no matter whether they have been encoded or not.
     * <p>
     * A codec should never act as a guard, but rather pass on all messages that it could not
     * handle. There is always the possibility that there is another codec in the pipeline that can
     * handle this object.
     *
     * @param ctx            the handler context
     * @param msg            the message that should be encoded
     * @param passOnConsumer to pass messages to the next handler in the pipeline
     */
    abstract void encode(HandlerContext ctx,
                         D msg,
                         Consumer<Object> passOnConsumer);

    /**
     * Decodes a given object of type {@code E} into type {@code D}.
     * <p>
     * You have to use the given {@code passOnConsumer} to pass all objects to the next handler in
     * the pipeline, no matter whether they have been decoded or not.
     * <p>
     * A codec should never act as a guard, but rather pass on all messages that it could not
     * handle. There is always the possibility that there is another codec in the pipeline that can
     * handle this object.
     *
     * @param ctx            the handler context
     * @param msg            the message that should be decoded
     * @param passOnConsumer to pass messages to the next handler in the pipeline
     */
    abstract void decode(HandlerContext ctx, E msg, Consumer<Object> passOnConsumer);
}