/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl;

import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.channel.DrasylNodeServerChannelInitializer;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.util.internal.NonNull;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Queue;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

public class EmbeddedNode extends DrasylNode implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedNode.class);
    private final Queue<Event> events;
    private boolean started;

    private EmbeddedNode(final DrasylConfig config,
                         final Queue<Event> events) throws DrasylException {
        super(config);
        this.events = requireNonNull(events);
    }

    public EmbeddedNode(final DrasylConfig config) throws DrasylException {
        this(config, new ArrayDeque<>());

        bootstrap.handler(new DrasylNodeServerChannelInitializer(config, this) {
            @Override
            protected void initChannel(final RustDrasylServerChannel ch) {
                super.initChannel(ch);
            }
        });
    }

    @Override
    public void onEvent(@NonNull final Event event) {
        if (event instanceof InboundExceptionEvent) {
            LOG.warn("{}", event, ((InboundExceptionEvent) event).getError());
        }

        events.add(event);
    }

    public EmbeddedNode awaitStarted() {
        if (!started) {
            started = true;
            start();
            await("NodeUpEvent").untilAsserted(() -> assertThat(readEvent(), instanceOf(NodeUpEvent.class)));
        }
        return this;
    }

    @Override
    public void close() {
        if (started) {
            started = false;
            shutdown().toCompletableFuture().join();
            // shutdown() future is completed before channelInactive has passed the pipeline...
            await("NodeNormalTerminationEvent").untilAsserted(() -> assertThat(readEvent(), instanceOf(NodeNormalTerminationEvent.class)));
        }
    }

    public int getPort() {
        final RustDrasylServerChannel channel = (RustDrasylServerChannel) this.pipeline().channel();
        await("port != 0").atMost(ofSeconds(5_000)).until(() -> channel.getUdpPort() != 0);
        return channel.getUdpPort();
    }

    /**
     * Return received user events from this {@link Channel}
     */
    @SuppressWarnings("unchecked")
    public <T> T readEvent() {
        final T event = (T) events.poll();
        if (event != null) {
            ReferenceCountUtil.touch(event, "Caller of readEvent() will handle the user event from this point");
        }
        return event;
    }

    @Override
    public String toString() {
        return identity.getAddress().toString();
    }
}
