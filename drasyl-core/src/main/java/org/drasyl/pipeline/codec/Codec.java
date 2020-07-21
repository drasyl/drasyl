package org.drasyl.pipeline.codec;

import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleDuplexHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Standard interface for all codecs of the {@link org.drasyl.pipeline.Pipeline}.
 * <br>
 * A codec can be used to encode/decode a given set of objects into the correct format to process
 * the object in the ongoing steps.
 * <br>
 * A codec must have a symmetrical construction. {@link #encode(HandlerContext, D, List)} converts
 * an object of type D into type E and {@link #decode(HandlerContext, E, List)} vice versa.
 *
 * <p>
 * <b>Note</b>: You can use the {@link HandlerContext#validator()} to check if a given {@code
 * Class} is allowed to be encode/decode.
 * </p>
 */
public abstract class Codec<E, D> extends SimpleDuplexHandler<E, Event, D> {
    @Override
    protected void matchedEventTriggered(HandlerContext ctx, Event event) {
        // Skip
        ctx.fireEventTriggered(event);
    }

    @Override
    protected void matchedRead(HandlerContext ctx, CompressedPublicKey sender, E msg) {
        // decode a given application message
        ArrayList<Object> out = new ArrayList<>();
        decode(ctx, msg, out);

        for (Object o : out) {
            ctx.fireRead(sender, o);
        }
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

        ArrayList<Object> out = new ArrayList<>();
        encode(ctx, msg, out);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Object o : out) {
            CompletableFuture<Void> newFuture = new CompletableFuture<>();
            futures.add(newFuture);
            ctx.write(recipient, o, newFuture);
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).handleAsync((t, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
            }
            else {
                future.complete(t);
            }

            return t;
        });
    }

    /**
     * Encodes a given object of type {@code D} into type {@code E}.
     *
     * <br>
     * If you want to skip a object, add it also to the {@code out} list.
     *
     * @param ctx the handler context
     * @param msg the message that should be encoded
     * @param out the output that should be passed to the next handler in the pipeline
     */
    abstract void encode(HandlerContext ctx, D msg, List<Object> out);

    /**
     * Decodes a given object of type {@code E} into type {@code D}.
     *
     * <br>
     * If you want to skip a object, add it also to the {@code out} list.
     *
     * @param ctx the handler context
     * @param msg the message that should be decoded
     * @param out the output that should be passed to the next handler in the pipeline
     */
    abstract void decode(HandlerContext ctx, E msg, List<Object> out);
}
