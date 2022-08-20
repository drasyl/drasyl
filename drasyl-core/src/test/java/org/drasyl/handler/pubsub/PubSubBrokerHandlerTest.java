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

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.Multimap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PubSubBrokerHandlerTest {
    @Nested
    class Publishing {
        @SuppressWarnings("unchecked")
        @Test
        void shouldDropPublicationIfThereAreNoSubscribers(@Mock final DrasylAddress subscriber,
                                                          @Mock final DrasylAddress sender,
                                                          @Mock final ByteBuf content) {
            final Multimap<String, DrasylAddress> subscriptions = new HashSetMultimap<>();
            subscriptions.put("animals/dog", subscriber);

            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubBrokerHandler(subscriptions));

            final PubSubPublish publish = PubSubPublish.of("animals/cat", content);
            channel.writeInbound(new OverlayAddressedMessage<>(publish, null, sender));

            // no publish / only confirmation message
            final Object published = channel.readOutbound();
            assertThat(published, instanceOf(OverlayAddressedMessage.class));
            assertThat(((OverlayAddressedMessage<?>) published).content(), instanceOf(PubSubPublished.class));
            assertEquals(publish.getId(), ((OverlayAddressedMessage<PubSubPublished>) published).content().getId());

            // no more messages
            assertNull(channel.readOutbound());
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldForwardPublicationToAllSubscribers(@Mock(name = "subscriber1") final DrasylAddress subscriber1,
                                                      @Mock(name = "subscriber2") final DrasylAddress subscriber2,
                                                      @Mock(name = "subscriber3") final DrasylAddress subscriber3,
                                                      @Mock final DrasylAddress sender,
                                                      @Mock final ByteBuf content) {
            final Multimap<String, DrasylAddress> subscriptions = new HashSetMultimap<>();
            subscriptions.put("animals/dog", subscriber1);
            subscriptions.put("animals/cat", subscriber2);
            subscriptions.put("animals/cat", subscriber3);

            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubBrokerHandler(subscriptions));

            final PubSubPublish publish = PubSubPublish.of("animals/cat", content);
            channel.writeInbound(new OverlayAddressedMessage<>(publish, null, sender));

            // publish messages
            final Collection<OverlayAddressedMessage<PubSubPublish>> outbound = new ArrayList<>(2);
            outbound.add(channel.readOutbound());
            outbound.add(channel.readOutbound());

            assertThat(outbound, containsInAnyOrder(new OverlayAddressedMessage<>(publish, subscriber2), new OverlayAddressedMessage<>(publish, subscriber3)));

            // confirmation message
            final Object published = channel.readOutbound();
            assertThat(published, instanceOf(OverlayAddressedMessage.class));
            assertThat(((OverlayAddressedMessage<?>) published).content(), instanceOf(PubSubPublished.class));
            assertEquals(publish.getId(), ((OverlayAddressedMessage<PubSubPublished>) published).content().getId());

            // no more messages
            assertNull(channel.readOutbound());
        }
    }

    @Nested
    class Subscribing {
        @SuppressWarnings("unchecked")
        @Test
        void shouldCreateSubscription(@Mock final DrasylAddress sender) {
            final Multimap<String, DrasylAddress> subscriptions = new HashSetMultimap<>();

            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubBrokerHandler(subscriptions));

            final PubSubSubscribe subscribe = PubSubSubscribe.of("animals/cat");
            channel.writeInbound(new OverlayAddressedMessage<>(subscribe, null, sender));

            // confirmation message
            final Object published = channel.readOutbound();
            assertThat(published, instanceOf(OverlayAddressedMessage.class));
            assertThat(((OverlayAddressedMessage<?>) published).content(), instanceOf(PubSubSubscribed.class));
            assertEquals(subscribe.getId(), ((OverlayAddressedMessage<PubSubSubscribed>) published).content().getId());

            // no more messages
            assertNull(channel.readOutbound());

            assertEquals(Set.of(sender), subscriptions.get("animals/cat"));
        }
    }

    @Nested
    class Unsubscribing {
        @SuppressWarnings("unchecked")
        @Test
        void shouldRemoveSubscription(@Mock final DrasylAddress sender) {
            final Multimap<String, DrasylAddress> subscriptions = new HashSetMultimap<>();
            subscriptions.put("animals/cat", sender);

            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubBrokerHandler(subscriptions));

            final PubSubUnsubscribe unsubscribe = PubSubUnsubscribe.of("animals/cat");
            channel.writeInbound(new OverlayAddressedMessage<>(unsubscribe, null, sender));

            // confirmation message
            final Object published = channel.readOutbound();
            assertThat(published, instanceOf(OverlayAddressedMessage.class));
            assertThat(((OverlayAddressedMessage<?>) published).content(), instanceOf(PubSubUnsubscribed.class));
            assertEquals(unsubscribe.getId(), ((OverlayAddressedMessage<PubSubUnsubscribed>) published).content().getId());

            // no more messages
            assertNull(channel.readOutbound());

            assertTrue(subscriptions.isEmpty());
        }
    }

    @Nested
    class ShutDown {
        @Test
        void shouldInformAllSubscribers(@Mock final DrasylAddress subscriber1,
                                        @Mock final DrasylAddress subscriber2,
                                        @Mock final DrasylAddress subscriber3) {
            final Multimap<String, DrasylAddress> subscriptions = new HashSetMultimap<>();
            subscriptions.putAll("cat", Set.of(subscriber1, subscriber2));
            subscriptions.put("dog", subscriber3);

            final EmbeddedChannel channel = new EmbeddedChannel(new PubSubBrokerHandler(subscriptions));
            assertTrue(channel.close().syncUninterruptibly().isSuccess());
        }
    }
}
