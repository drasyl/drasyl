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
package org.drasyl.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ServerChannel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.INBOUND_SERIALIZATION_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.OUTBOUND_SERIALIZATION_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.PEERS_MANAGER_ATTR_KEY;

/**
 * A {@link EmbeddedChannel} based on a {@link EmbeddedDrasylServerChannel}.
 */
public class EmbeddedDrasylServerChannel extends EmbeddedChannel implements ServerChannel {
    private final Subject<Object> events;

    public EmbeddedDrasylServerChannel(final DrasylConfig config,
                                       final Identity identity,
                                       final PeersManager peersManager,
                                       final Serialization inboundSerialization,
                                       final Serialization outboundSerialization,
                                       final Subject<Object> events,
                                       final ChannelHandler... handlers) {
        this.events = requireNonNull(events);

        attr(CONFIG_ATTR_KEY).set(requireNonNull(config));
        attr(IDENTITY_ATTR_KEY).set(requireNonNull(identity));
        attr(PEERS_MANAGER_ATTR_KEY).set(requireNonNull(peersManager));
        attr(INBOUND_SERIALIZATION_ATTR_KEY).set(requireNonNull(inboundSerialization));
        attr(OUTBOUND_SERIALIZATION_ATTR_KEY).set(requireNonNull(outboundSerialization));

        pipeline().addLast(new ChannelInitializer<>() {
            @Override
            protected void initChannel(final Channel ch) {
                final ChannelPipeline pipeline = ch.pipeline();
                for (final ChannelHandler h : handlers) {
                    if (h == null) {
                        break;
                    }
                    pipeline.addLast(h);
                }
            }
        });

        pipeline().addLast("EVENTS_ACCEPTOR", new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) throws Exception {
                events.onNext(evt);

                super.userEventTriggered(ctx, evt);
            }
        });
    }

    public EmbeddedDrasylServerChannel(final DrasylConfig config,
                                       final Identity identity,
                                       final PeersManager peersManager,
                                       final ChannelHandler... handlers) {
        this(
                config,
                identity,
                peersManager,
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()),
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound()),
                ReplaySubject.create().toSerialized(),
                handlers
        );
    }

    /**
     * @return all events that passes the pipeline until the end
     */
    public Observable<Object> events() {
        return events;
    }

    public void drasylClose() {
        releaseOutbound();
        releaseInbound();
        events.onComplete();

        close();
    }
}
