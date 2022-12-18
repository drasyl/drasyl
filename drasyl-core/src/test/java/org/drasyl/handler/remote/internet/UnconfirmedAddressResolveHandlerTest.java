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
package org.drasyl.handler.remote.internet;

import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnconfirmedAddressResolveHandlerTest {
    @Test
    void shouldResolveOverlayAddressToInetAddressesDiscoveryThroughReceivedMessages(@Mock final RemoteMessage remoteMsg,
                                                                                    @Mock final DrasylAddress overlayAddress,
                                                                                    @Mock final InetSocketAddress inetAddress) {
        when(remoteMsg.getSender()).thenReturn(overlayAddress);

        final EmbeddedChannel channel = new EmbeddedChannel(new UnconfirmedAddressResolveHandler());

        // never received message from recipient -> overlay address cannot be resolved
        final OverlayAddressedMessage<RemoteMessage> msg1 = new OverlayAddressedMessage<>(remoteMsg, overlayAddress);
        channel.writeOutbound(msg1);
        assertEquals(msg1, channel.readOutbound());

        // got message from recipient -> save inet address
        final InetAddressedMessage<RemoteMessage> msg2 = new InetAddressedMessage<>(remoteMsg, null, inetAddress);
        channel.writeInbound(msg2);

        // resolve overlay address to previously discovered inet address
        channel.writeOutbound(msg1);
        assertEquals(new InetAddressedMessage<>(remoteMsg, inetAddress), channel.readOutbound());
    }
}
