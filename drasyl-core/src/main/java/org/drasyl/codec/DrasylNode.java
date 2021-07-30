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
package org.drasyl.codec;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.DrasylAddress;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.annotation.NonNull;
import org.drasyl.annotation.Nullable;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.serialization.MessageSerializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.drasyl.codec.Null.NULL;

public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);
    private static String version;
    private final DrasylBootstrap bootstrap;
    private Channel channel;
    private ChannelFuture channelFuture;

    protected DrasylNode() throws DrasylException {
        this(DrasylConfig.of());
    }

    protected DrasylNode(final DrasylConfig config) throws DrasylException {
        try {
            bootstrap = new DrasylBootstrap(config, this::onEvent)
                    .handler(new DrasylServerChannelInitializer() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            ch.pipeline().addFirst(new DrasylNodeHandler());

                            super.initChannel(ch);
                        }
                    })
                    .childHandler(new ChannelInitializer<DrasylChannel>() {
                        @Override
                        protected void initChannel(final DrasylChannel ch) throws Exception {
                            ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(final ChannelHandlerContext ctx,
                                                        Object msg) {
                                    if (msg == NULL) {
                                        msg = null;
                                    }

                                    final MessageEvent event = MessageEvent.of((IdentityPublicKey) ctx.channel().remoteAddress(), msg);
                                    onEvent(event);
                                }
                            });
                        }
                    })
            ;

            LOG.debug("drasyl node with config `{}` and identity `{}` created", config, bootstrap.identity());
        }
        catch (final IOException e) {
            throw new DrasylException("Couldn't load or create identity", e);
        }
    }

    /**
     * Returns the version of the node. If the version could not be read, {@code null} is returned.
     *
     * @return the version of the node. If the version could not be read, {@code null} is returned
     */
    @Nullable
    public static String getVersion() {
        if (version == null) {
            final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/org.drasyl.versions.properties");
            if (inputStream != null) {
                try {
                    final Properties properties = new Properties();
                    properties.load(inputStream);
                    version = properties.getProperty("version");
                }
                catch (final IOException e) {
                    LOG.debug("Unable to read properties file.", e);
                }
            }
        }

        return version;
    }

    /**
     * Sends <code>event</code> to the application and tells it information about the local node,
     * other peers, connections or incoming messages.
     *
     * @param event the event
     */
    public abstract void onEvent(@NonNull Event event);

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. Serializable objects can be added on
     * start via the {@link DrasylConfig} or on demand via {@link HandlerContext#inboundSerialization()}
     * or {@link HandlerContext#outboundSerialization()}.
     * </p>
     *
     * @param recipient the recipient of a message as compressed public key
     * @param payload   the payload of a message
     * @return a completion stage if the message was successfully processed, otherwise an
     * exceptionally completion stage
     * @see org.drasyl.pipeline.Handler
     * @see MessageSerializer
     * @since 0.1.3-SNAPSHOT
     */
    @NonNull
    public CompletionStage<Void> send(@NonNull final String recipient,
                                      final Object payload) {
        try {
            return send(IdentityPublicKey.of(recipient), payload);
        }
        catch (final IllegalArgumentException e) {
            return failedFuture(new DrasylException("Recipient does not conform to a valid public key.", e));
        }
    }

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. Serializable objects can be added on
     * start via the {@link DrasylConfig} or on demand via {@link HandlerContext#inboundSerialization()}
     * or {@link HandlerContext#outboundSerialization()}.
     * </p>
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completion stage if the message was successfully processed, otherwise an
     * exceptionally completion stage
     * @see org.drasyl.pipeline.Handler
     * @see MessageSerializer
     * @since 0.1.3-SNAPSHOT
     */
    @NonNull
    public CompletionStage<Void> send(@Nullable final DrasylAddress recipient,
                                      final Object payload) {
        if (channel != null) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            channel.pipeline().fireUserEventTriggered(new OutboundMessage(payload, recipient, future));
            return future;
        }
        else {
            return failedFuture(new Exception("You have to call DrasylNode#start() first!"));
        }
    }

    @NonNull
    public synchronized CompletableFuture<Void> shutdown() {
        if (channel != null) {
            final ChannelFuture channelFuture = channel.close();
            channel = null;
            final CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    this.channelFuture = null;
                    completableFuture.complete(null);
                }
                else {
                    completableFuture.completeExceptionally(future.cause());
                }
            });
            return completableFuture;
        }
        else {
            return completedFuture(null);
        }
    }

    @NonNull
    public synchronized CompletableFuture<Void> start() {
        if (channelFuture == null) {
            channelFuture = bootstrap.bind();
        }
        final CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                completableFuture.complete(null);
            }
            else {
                completableFuture.completeExceptionally(future.cause());
            }
        });
        return completableFuture;
    }

    private class DrasylNodeHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) {
            if (evt instanceof Event) {
                onEvent((Event) evt);
            }
            else if (evt instanceof OutboundMessage) {
                final DrasylAddress recipient = ((OutboundMessage) evt).getRecipient();
                Object payload = ((OutboundMessage) evt).getPayload();
                final CompletableFuture<Void> future = ((OutboundMessage) evt).getFuture();

                final Channel peerChannel = ((DrasylServerChannel) ctx.channel()).getOrCreateChildChannel(ctx, (IdentityPublicKey) recipient);

                if (payload == null) {
                    payload = NULL;
                }

                peerChannel.writeAndFlush(payload).addListener(f -> {
                    if (f.isSuccess()) {
                        future.complete(null);
                    }
                    else {
                        future.completeExceptionally(f.cause());
                    }
                });
            }
            else {
                ctx.fireUserEventTriggered(ctx);
            }
        }
    }

    public static class OutboundMessage {
        private final Object payload;
        private final DrasylAddress recipient;
        private final CompletableFuture<Void> future;

        public OutboundMessage(final Object payload,
                               final DrasylAddress recipient,
                               final CompletableFuture<Void> future) {
            this.payload = payload;
            this.recipient = requireNonNull(recipient);
            this.future = requireNonNull(future);
        }

        public Object getPayload() {
            return payload;
        }

        public DrasylAddress getRecipient() {
            return recipient;
        }

        public CompletableFuture<Void> getFuture() {
            return future;
        }
    }

    /**
     * Returns the {@link Pipeline} to allow users to add own handlers.
     *
     * @return the pipeline
     */
    @NonNull
    public ChannelPipeline pipeline() {
        return channel.pipeline();
    }

    /**
     * Returns the {@link Identity} of this node.
     *
     * @return the {@link Identity} of this node
     */
    @NonNull
    public Identity identity() {
        return bootstrap.identity();
    }
}
