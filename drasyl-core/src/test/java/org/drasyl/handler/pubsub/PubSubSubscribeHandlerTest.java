/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.pubsub;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Promise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PubSubSubscribeHandlerTest {
    private final UUID id = new UUID(-5_473_769_416_544_107_185L, 6_439_925_875_238_784_627L);

    @Test
    void shouldPassOutboundSubscribeRequestToBroker(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                    @Mock final DrasylAddress broker,
                                                    @Mock final Set<String> subscriptions) {
        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubSubscribe subscribe = PubSubSubscribe.of("myTopic");
        channel.writeOutbound(subscribe);

        assertEquals(new OverlayAddressedMessage<>(subscribe, broker), channel.readOutbound());
        verify(requests).put(eq(subscribe.getId()), any());
    }

    @Test
    void shouldPassOutboundUnsubscribeRequestToBroker(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                      @Mock final DrasylAddress broker,
                                                      @Mock final Set<String> subscriptions) {
        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubUnsubscribe unsubscribe = PubSubUnsubscribe.of("myTopic");
        channel.writeOutbound(unsubscribe);

        assertEquals(new OverlayAddressedMessage<>(unsubscribe, broker), channel.readOutbound());
        verify(requests).put(eq(unsubscribe.getId()), any());
    }

    @Test
    void shouldHandleInboundSubscribedResponse(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                               @Mock final DrasylAddress broker,
                                               @Mock final Set<String> subscriptions,
                                               @Mock final Promise<Void> promise) {
        when(requests.remove(id)).thenReturn(Pair.of(promise, "myTopic"));

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubSubscribed subscribed = PubSubSubscribed.of(id);
        channel.writeInbound(new OverlayAddressedMessage<>(subscribed, null, broker));

        verify(subscriptions).add("myTopic");
        verify(promise).trySuccess(null);
    }

    @Test
    void shouldHandleInboundUnsubscribedResponse(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                 @Mock final DrasylAddress broker,
                                                 @Mock final Set<String> subscriptions,
                                                 @Mock final Promise<Void> promise) {
        when(requests.remove(id)).thenReturn(Pair.of(promise, "myTopic"));

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubUnsubscribed unsubscribed = PubSubUnsubscribed.of(id);
        channel.writeInbound(new OverlayAddressedMessage<>(unsubscribed, null, broker));

        verify(subscriptions).remove("myTopic");
        verify(promise).trySuccess(null);
    }

    @Test
    void shouldPassInboundPublishNotificationForExistingSubscription(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                                     @Mock final DrasylAddress broker,
                                                                     @Mock final Set<String> subscriptions) {
        when(subscriptions.contains("myTopic")).thenReturn(true);

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubPublish publish = PubSubPublish.of(id, "myTopic", Unpooled.EMPTY_BUFFER);
        channel.writeInbound(new OverlayAddressedMessage<>(publish, null, broker));

        assertEquals(publish, channel.readInbound());
    }

    @Test
    void shouldDropInboundPublishNotificationForNotExistingSubscription(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                                        @Mock final DrasylAddress broker,
                                                                        @Mock final Set<String> subscriptions) {
        when(subscriptions.contains("myTopic")).thenReturn(false);

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubPublish publish = PubSubPublish.of(id, "myTopic", Unpooled.buffer());
        channel.writeInbound(new OverlayAddressedMessage<>(publish, null, broker));

        assertEquals(0, publish.refCnt());
    }

    @Test
    void shouldHandleInboundUnsubscribeNotification(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                    @Mock final DrasylAddress broker,
                                                    @Mock final Set<String> subscriptions) {
        when(subscriptions.remove("myTopic")).thenReturn(true);

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));

        final PubSubUnsubscribe unsubscribe = PubSubUnsubscribe.of(id, "myTopic");
        channel.writeInbound(new OverlayAddressedMessage<>(unsubscribe, null, broker));

        verify(subscriptions).remove("myTopic");
    }

    @Test
    void shouldUnsubscribeFromAllTopicsOnShutdown(@Mock final Map<UUID, Pair<Promise<Void>, String>> requests,
                                                  @Mock final DrasylAddress broker) {
        final Set<String> subscriptions = new HashSet<>(Set.of("foo", "bar"));

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubSubscribeHandler(5_000L, requests, broker, subscriptions));
        assertTrue(channel.close().awaitUninterruptibly().isSuccess());
    }
}
