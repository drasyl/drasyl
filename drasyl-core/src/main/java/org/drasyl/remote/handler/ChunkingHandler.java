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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.DrasylAddress;
import org.drasyl.annotation.NonNull;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.remote.protocol.ChunkMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.protocol.RemoteMessage.MAGIC_NUMBER_LENGTH;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler is responsible for merging incoming message chunks into a single message as well as
 * splitting outgoing too large messages into chunks.
 */
@SuppressWarnings({ "java:S110" })
public class ChunkingHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkingHandler.class);
    private final Worm<Map<Nonce, ChunksCollector>> chunksCollectors;
    private final DrasylAddress myAddress;
    private final int messageMaxContentLength;
    private final int messageMtu;
    private final Duration messageComposedMessageTransferTimeout;

    public ChunkingHandler(final int messageMaxContentLength,
                           final int messageMtu,
                           final Duration messageComposedMessageTransferTimeout,
                           final DrasylAddress myAddress) {
        this.myAddress = requireNonNull(myAddress);
        this.messageMaxContentLength = messageMaxContentLength;
        this.messageMtu = messageMtu;
        this.messageComposedMessageTransferTimeout = messageComposedMessageTransferTimeout;
        this.chunksCollectors = Worm.of();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws IOException {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof ChunkMessage) {
            final ChunkMessage chunkMsg = (ChunkMessage) ((AddressedMessage<?, ?>) msg).message();
            final SocketAddress sender = ((AddressedMessage<?, ?>) msg).address();

            // message is addressed to me
            if (myAddress.equals(chunkMsg.getRecipient())) {
                handleInboundChunk(ctx, sender, chunkMsg, new CompletableFuture<>());
            }
            else {
                // passthrough all messages not addressed to us
                ctx.fireChannelRead(msg);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws IOException {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) ((AddressedMessage<?, ?>) msg).message();
            final SocketAddress recipient = ((AddressedMessage<?, ?>) msg).address();

            if (myAddress.equals(remoteMsg.getSender())) {
                // message from us, check if we have to chunk it
                final ByteBuf messageByteBuf = ctx.alloc().ioBuffer();
                remoteMsg.writeTo(messageByteBuf);
                final int messageLength = messageByteBuf.readableBytes();
                if (messageMaxContentLength > 0 && messageLength > messageMaxContentLength) {
                    ReferenceCountUtil.safeRelease(messageByteBuf);
                    LOG.debug("The message has a size of {} bytes and is too large. The max. allowed size is {} bytes. Message dropped.", messageLength, messageMaxContentLength);
                }
                else if (messageLength > messageMtu) {
                    // message is too big, we have to chunk it
                    chunkMessage(ctx, recipient, remoteMsg, FutureUtil.toFuture(promise), messageByteBuf, messageLength);
                }
                else {
                    ReferenceCountUtil.safeRelease(messageByteBuf);
                    // message is small enough. No chunking required
                    ctx.write(msg, promise);
                }
            }
            else {
                // message not from us. Passthrough
                ctx.write(msg, promise);
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void handleInboundChunk(final ChannelHandlerContext ctx,
                                    final SocketAddress sender,
                                    final ChunkMessage chunk,
                                    final CompletableFuture<Void> future) throws IOException {
        try {
            final ChunksCollector chunksCollector = getChunksCollectors().computeIfAbsent(chunk.getNonce(), id -> new ChunksCollector(messageMaxContentLength, id, ctx.alloc()));
            final RemoteMessage message = chunksCollector.addChunk(chunk);

            if (message != null) {
                // message complete, pass it inbound
                getChunksCollectors().remove(chunk.getNonce());
                ctx.fireChannelRead(new AddressedMessage<>(message, sender));
            }
            else {
                // other chunks missing, but this chunk has been processed
                future.complete(null);
            }
        }
        catch (final IllegalStateException e) {
            getChunksCollectors().remove(chunk.getNonce());
            throw e;
        }
    }

    private Map<Nonce, ChunksCollector> getChunksCollectors() {
        return chunksCollectors.getOrCompute(() -> CacheBuilder.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(messageComposedMessageTransferTimeout)
                .removalListener((RemovalListener<Nonce, ChunksCollector>) entry -> {
                    if (entry.getValue().hasChunks()) {
                        //noinspection unchecked
                        LOG.debug("Not all chunks of message `{}` were received within {}ms ({} of {} present). Message dropped.", entry::getKey, messageComposedMessageTransferTimeout::toMillis, entry.getValue()::getPresentChunks, entry.getValue()::getTotalChunks);
                        entry.getValue().release();
                    }
                })
                .build()
                .asMap());
    }

    @SuppressWarnings("unchecked")
    private void chunkMessage(final ChannelHandlerContext ctx,
                              final SocketAddress recipient,
                              final RemoteMessage msg,
                              final CompletableFuture<Void> future,
                              final ByteBuf messageByteBuf,
                              final int messageSize) throws IOException {
        try {
            // create & send chunks
            UnsignedShort chunkNo = UnsignedShort.of(0);

            final PublicHeader partialChunkHeader = PublicHeader.newBuilder()
                    .setNonce(msg.getNonce().toByteString())
                    .setSender(msg.getSender().getBytes())
                    .setRecipient(msg.getRecipient().getBytes())
                    .setHopCount(1)
                    .setTotalChunks(UnsignedShort.MAX_VALUE.getValue())
                    .buildPartial();

            final UnsignedShort totalChunks = totalChunks(messageSize, messageMtu, partialChunkHeader);
            LOG.debug("The message `{}` has a size of {} bytes and is therefore split into {} chunks (MTU = {}).", () -> sanitizeLogArg(msg), () -> messageSize, () -> totalChunks, () -> messageMtu);

            final FutureCombiner combiner = FutureCombiner.getInstance();
            final int chunkSize = getChunkSize(partialChunkHeader, messageMtu);

            while (messageByteBuf.readableBytes() > 0) {
                ByteBuf chunkBodyByteBuf = null;
                final ByteBuf chunkByteBuf = ctx.alloc().ioBuffer();
                try (final ByteBufOutputStream outputStream = new ByteBufOutputStream(chunkByteBuf)) {
                    RemoteMessage.MAGIC_NUMBER.writeTo(outputStream);

                    // chunk header
                    final PublicHeader chunkHeader = buildChunkHeader(totalChunks, partialChunkHeader, chunkNo);
                    chunkHeader.writeDelimitedTo(outputStream);

                    // chunk body
                    final int chunkBodyLength = Math.min(messageByteBuf.readableBytes(), chunkSize);
                    chunkBodyByteBuf = messageByteBuf.readRetainedSlice(chunkBodyLength);
                    chunkByteBuf.writeBytes(chunkBodyByteBuf);

                    // send chunk
                    final RemoteMessage chunk = PartialReadMessage.of(chunkByteBuf);

                    final CompletableFuture<Void> future1 = new CompletableFuture<>();
                    FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(chunk, recipient)))).combine(future1);
                    combiner.add(future1);
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
