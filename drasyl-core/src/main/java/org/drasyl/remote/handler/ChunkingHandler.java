/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.remote.handler;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.DrasylConfig;
import org.drasyl.annotation.NonNull;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.remote.protocol.RemoteEnvelope.MAGIC_NUMBER_LENGTH;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler is responsible for merging incoming message chunks into a single message as well as
 * splitting outgoing too large messages into chunks.
 */
@SuppressWarnings({ "java:S110" })
public class ChunkingHandler extends SimpleDuplexHandler<RemoteEnvelope<? extends MessageLite>, RemoteEnvelope<? extends MessageLite>, InetSocketAddressWrapper> {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkingHandler.class);
    private final Worm<Map<Nonce, ChunksCollector>> chunksCollectors;

    public ChunkingHandler() {
        this.chunksCollectors = Worm.of();
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final InetSocketAddressWrapper sender,
                                  final RemoteEnvelope<? extends MessageLite> msg,
                                  final CompletableFuture<Void> future) {
        try {
            // message is addressed to me and chunked
            if (ctx.identity().getIdentityPublicKey().equals(msg.getRecipient()) && msg.isChunk()) {
                handleInboundChunk(ctx, sender, msg, future);
            }
            else {
                // passthrough all messages not addressed to us
                ctx.passInbound(sender, msg, future);
            }
        }
        catch (final IOException e) {
            future.completeExceptionally(new Exception("Unable to read message.", e));
            LOG.debug("Can't read message `{}` due to the following error: ", () -> sanitizeLogArg(msg), () -> e);
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private void handleInboundChunk(final HandlerContext ctx,
                                    final InetSocketAddressWrapper sender,
                                    final RemoteEnvelope<? extends MessageLite> chunk,
                                    final CompletableFuture<Void> future) throws IOException {
        try {
            final ChunksCollector chunksCollector = getChunksCollectors(ctx.config()).computeIfAbsent(chunk.getNonce(), id -> new ChunksCollector(ctx.config().getRemoteMessageMaxContentLength(), id));
            final RemoteEnvelope<? extends MessageLite> message = chunksCollector.addChunk(chunk);

            if (message != null) {
                // message complete, pass it inbound
                getChunksCollectors(ctx.config()).remove(chunk.getNonce());
                ctx.passInbound(sender, message, future);
            }
            else {
                // other chunks missing, but this chunk has been processed
                future.complete(null);
            }
        }
        catch (final IllegalStateException e) {
            getChunksCollectors(ctx.config()).remove(chunk.getNonce());
            throw e;
        }
    }

    private Map<Nonce, ChunksCollector> getChunksCollectors(final DrasylConfig config) {
        return chunksCollectors.getOrCompute(() -> CacheBuilder.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(config.getRemoteMessageComposedMessageTransferTimeout())
                .removalListener((RemovalListener<Nonce, ChunksCollector>) entry -> {
                    if (entry.getValue().hasChunks()) {
                        //noinspection unchecked
                        LOG.debug("Not all chunks of message `{}` were received within {}ms ({} of {} present). Message dropped.", entry::getKey, config.getRemoteMessageComposedMessageTransferTimeout()::toMillis, entry.getValue()::getPresentChunks, entry.getValue()::getTotalChunks);
                        entry.getValue().release();
                    }
                })
                .build()
                .asMap());
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final InetSocketAddressWrapper recipient,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) {
        try {
            if (ctx.identity().getIdentityPublicKey().equals(msg.getSender())) {
                // message from us, check if we have to chunk it
                final ByteBuf messageByteBuf = msg.getOrBuildByteBuf();
                final int messageLength = messageByteBuf.readableBytes();
                final int messageMaxContentLength = ctx.config().getRemoteMessageMaxContentLength();
                if (messageMaxContentLength > 0 && messageLength > messageMaxContentLength) {
                    //noinspection unchecked
                    LOG.debug("The message `{}` has a size of {} bytes and is too large. The max allowed size is {} bytes. Message dropped.", () -> sanitizeLogArg(msg), () -> messageLength, () -> messageMaxContentLength);
                    future.completeExceptionally(new Exception("The message has a size of " + messageLength + " bytes and is too large. The max. allowed size is " + messageMaxContentLength + " bytes. Message dropped."));
                    ReferenceCountUtil.safeRelease(messageByteBuf);
                }
                else if (messageLength > ctx.config().getRemoteMessageMtu()) {
                    // message is too big, we have to chunk it
                    chunkMessage(ctx, recipient, msg, future, messageByteBuf, messageLength);
                }
                else {
                    // message is small enough. No chunking required
                    ctx.passOutbound(recipient, msg, future);
                }
            }
            else {
                ctx.passOutbound(recipient, msg, future);
            }
        }
        catch (final IllegalStateException | IOException e) {
            future.completeExceptionally(new Exception("Unable to read message.", e));
            LOG.debug("Can't read message `{}` due to the following error: ", () -> sanitizeLogArg(msg), () -> e);
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @SuppressWarnings("unchecked")
    private static void chunkMessage(final HandlerContext ctx,
                                     final Address recipient,
                                     final RemoteEnvelope<? extends MessageLite> msg,
                                     final CompletableFuture<Void> future,
                                     final ByteBuf messageByteBuf,
                                     final int messageSize) throws IOException {
        try {
            // create & send chunks
            final PublicHeader msgPublicHeader = msg.getPublicHeader();
            UnsignedShort chunkNo = UnsignedShort.of(0);

            final PublicHeader partialChunkHeader = PublicHeader.newBuilder()
                    .setNonce(msgPublicHeader.getNonce())
                    .setSender(msgPublicHeader.getSender())
                    .setRecipient(msgPublicHeader.getRecipient())
                    .setHopCount(1)
                    .setTotalChunks(UnsignedShort.MAX_VALUE.getValue())
                    .buildPartial();

            final int mtu = ctx.config().getRemoteMessageMtu();
            final UnsignedShort totalChunks = totalChunks(messageSize, mtu, partialChunkHeader);
            LOG.debug("The message `{}` has a size of {} bytes and must be split to {} chunks (MTU = {}).", () -> sanitizeLogArg(msg), () -> messageSize, () -> totalChunks, () -> mtu);

            final FutureCombiner combiner = FutureCombiner.getInstance();
            final int chunkSize = getChunkSize(partialChunkHeader, mtu);

            while (messageByteBuf.readableBytes() > 0) {
                ByteBuf chunkBodyByteBuf = null;
                final ByteBuf chunkByteBuf = PooledByteBufAllocator.DEFAULT.buffer();
                try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(chunkByteBuf)) {
                    RemoteEnvelope.MAGIC_NUMBER.writeTo(outputStream);

                    // chunk header
                    final PublicHeader chunkHeader = buildChunkHeader(totalChunks, partialChunkHeader, chunkNo);
                    chunkHeader.writeDelimitedTo(outputStream);

                    // chunk body
                    final int chunkBodyLength = Math.min(messageByteBuf.readableBytes(), chunkSize);
                    chunkBodyByteBuf = messageByteBuf.readRetainedSlice(chunkBodyLength);
                    chunkByteBuf.writeBytes(chunkBodyByteBuf);

                    // send chunk
                    final RemoteEnvelope<? extends MessageLite> chunk = RemoteEnvelope.of(chunkByteBuf);

                    combiner.add(ctx.passOutbound(recipient, chunk, new CompletableFuture<>()));
                }
                finally {
                    ReferenceCountUtil.safeRelease(chunkBodyByteBuf);
                }

                chunkNo = chunkNo.increment();
            }

            combiner.combine(future);
        }
        finally {
            ReferenceCountUtil.safeRelease(messageByteBuf);
        }
    }

    @NonNull
    private static PublicHeader buildChunkHeader(final UnsignedShort totalChunks,
                                                 final PublicHeader partialHeader,
                                                 final UnsignedShort chunkNo) {
        final PublicHeader.Builder builder = PublicHeader.newBuilder(partialHeader);
        builder.clearTotalChunks();

        if (chunkNo.getValue() == 0) {
            // set only on first chunk (head chunk)
            builder.setTotalChunks(totalChunks.getValue());
        }
        else {
            // set on all non-head chunks
            builder.setChunkNo(chunkNo.getValue());
        }

        return builder.build();
    }

    /**
     * Calculates how much chunks are required to send the payload of the given size with the given
     * max mtu value.
     *
     * @param payloadSize the size of the payload
     * @param mtu         the fixed mtu value
     * @param header      the header of each chunk
     * @return the total amount of chunks required to send the given payload
     */
    private static UnsignedShort totalChunks(final int payloadSize,
                                             final int mtu,
                                             final PublicHeader header) {
        final double chunkSize = getChunkSize(header, mtu);
        final int totalChunks = (int) Math.ceil(payloadSize / chunkSize);

        return UnsignedShort.of(totalChunks);
    }

    /**
     * Calculates the chunk size.
     *
     * @param header the header of each chunk
     * @param mtu    the mtu value
     * @return the size of each chunk
     */
    private static int getChunkSize(final PublicHeader header, final int mtu) {
        final int headerSize = header.getSerializedSize();

        return mtu - (MAGIC_NUMBER_LENGTH + CodedOutputStream.computeUInt32SizeNoTag(headerSize) + headerSize);
    }
}
