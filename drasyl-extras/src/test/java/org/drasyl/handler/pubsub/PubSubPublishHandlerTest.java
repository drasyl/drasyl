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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PubSubPublishHandlerTest {
    private final UUID id = new UUID(-5_473_769_416_544_107_185L, 6_439_925_875_238_784_627L);

    @Test
    void shouldPassOutboundPublishRequestToBroker(@Mock final Map<UUID, Promise<Void>> requests,
                                                  @Mock final DrasylAddress broker) {
        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubPublishHandler(ofMillis(5_000L), requests, broker));

        final PubSubPublish publish = PubSubPublish.of("myTopic", Unpooled.buffer());
        channel.writeOutbound(publish);

        assertEquals(new OverlayAddressedMessage<>(publish, broker), channel.readOutbound());
        verify(requests).put(eq(publish.getId()), any());
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Test
    void shouldHandlePublishedResponse(@Mock final Map<UUID, Promise<Void>> requests,
                                       @Mock final DrasylAddress broker,
                                       @Mock final Promise<Void> promise) {
        when(requests.remove(any())).thenReturn(promise);

        final EmbeddedChannel channel = new EmbeddedChannel(new PubSubPublishHandler(ofMillis(5_000L), requests, broker));

        final PubSubPublished published = PubSubPublished.of(id);
        channel.writeInbound(new OverlayAddressedMessage<>(published, null, broker));

        verify(promise).trySuccess(null);
    }
}
