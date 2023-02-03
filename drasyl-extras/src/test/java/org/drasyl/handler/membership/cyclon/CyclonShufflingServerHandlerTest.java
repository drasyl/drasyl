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
package org.drasyl.handler.membership.cyclon;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static test.util.EqualNeighborWithSameAge.equalNeighborWithSameAge;

@ExtendWith(MockitoExtension.class)
class CyclonShufflingServerHandlerTest {
    @SuppressWarnings("unchecked")
    @Test
    void shouldCreateProperResponseAndUpdateOwnView(@Mock(name = "localAddress") final DrasylAddress localAddress,
                                                    @Mock(name = "sender") final DrasylAddress sender,
                                                    @Mock(name = "address0") final DrasylAddress address0,
                                                    @Mock(name = "address1") final DrasylAddress address1,
                                                    @Mock(name = "address2") final DrasylAddress address2,
                                                    @Mock(name = "address3") final DrasylAddress address3,
                                                    @Mock(name = "address4") final DrasylAddress address4) {
        // arrange
        final CyclonView view = CyclonView.of(4, Set.of(
                CyclonNeighbor.of(address0, 2),
                CyclonNeighbor.of(address1, 0),
                CyclonNeighbor.of(address2, 1),
                CyclonNeighbor.of(address3, 0)
        ));
        final CyclonShuffleRequest request = CyclonShuffleRequest.of(
                CyclonNeighbor.of(sender, 0),
                CyclonNeighbor.of(address3, 0),
                CyclonNeighbor.of(address4, 2)
        );

        // act
        final ChannelHandler handler = new CyclonShufflingServerHandler(3, view, ctx -> localAddress);
        final EmbeddedChannel ch = new EmbeddedChannel(handler);
        ch.writeInbound(new OverlayAddressedMessage<>(request, null, sender));

        // assert
        final Object o = ch.readOutbound();
        assertNotNull(o);
        assertThat(o, instanceOf(AddressedEnvelope.class));
        assertThat(((AddressedEnvelope<?, ?>) o).content(), instanceOf(CyclonShuffleResponse.class));
        final AddressedEnvelope<CyclonShuffleResponse, ?> response = (AddressedEnvelope<CyclonShuffleResponse, ?>) o;
        assertEquals(sender, response.recipient());
        assertThat(response.content().getNeighbors(), hasSize(3)); // size equal to shuffle size

        assertThat(view.getNeighbors(), hasSize(4)); // not bigger than view size
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(sender, 0))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address4, 2))));
    }
}
