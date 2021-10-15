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
package org.drasyl.handler.monitoring;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryHandlerTest {
    @Mock
    private Map<DrasylAddress, InetSocketAddress> superPeers;
    @Mock
    protected Map<DrasylAddress, InetSocketAddress> childrenPeers;
    @Mock
    protected Map<DrasylAddress, InetSocketAddress> peers;
    @Mock
    private HttpClient httpClient;
    private final URI uri;

    {
        try {
            uri = new URI("http://example.com/ping");
        }
        catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldSendTelemetryData(@Mock final DrasylAddress localAddress) {
        final ChannelHandler handler = new TelemetryHandler(superPeers, childrenPeers, peers, httpClient, 1, uri, false);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(localAddress, handler);
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            verify(httpClient).sendAsync(argThat(new HttpRequestUriMatcher(uri)), any());
        });
        channel.close();
    }

    private static class HttpRequestUriMatcher implements ArgumentMatcher<HttpRequest> {
        private final URI left;

        private HttpRequestUriMatcher(final URI left) {
            this.left = requireNonNull(left);
        }

        @Override
        public boolean matches(final HttpRequest o) {
            return left.equals(o.uri());
        }
    }
}
