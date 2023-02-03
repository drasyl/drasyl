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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static test.util.EqualNeighborWithSameAge.equalNeighborWithSameAge;

@ExtendWith(MockitoExtension.class)
class CyclonShufflingClientHandlerTest {
    @Captor
    ArgumentCaptor<AddressedEnvelope<CyclonShuffleRequest, ?>> outboundMsg;

    @Test
    void shouldCreateProperRequestToOldestNeighborAndUpdateOwnView(@Mock(name = "localAddress") final DrasylAddress localAddress,
                                                                   @Mock(name = "address0") final DrasylAddress address0,
                                                                   @Mock(name = "address1") final DrasylAddress address1,
                                                                   @Mock(name = "address2") final DrasylAddress address2,
                                                                   @Mock(name = "address3") final DrasylAddress address3,
                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
        // arrange
        when(ctx.channel().localAddress()).thenReturn(localAddress);
        final CyclonView view = CyclonView.of(4, Set.of(
                CyclonNeighbor.of(address0, 0),
                CyclonNeighbor.of(address1, 1),
                CyclonNeighbor.of(address2, 1),
                CyclonNeighbor.of(address3, 2)
        ));

        // act
        final CyclonShufflingClientHandler handler = new CyclonShufflingClientHandler(2, 100, view, null);
        handler.initiateShuffle(ctx);

        // assert
        verify(ctx).writeAndFlush(outboundMsg.capture());
        final AddressedEnvelope<CyclonShuffleRequest, ?> request = outboundMsg.getValue();
        assertEquals(address3, request.recipient());
        assertThat(request.content().getNeighbors(), hasSize(2)); // equal to shuffle size
        assertThat(request.content().getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(localAddress, 0)))); // sender must be included
        assertThat(request.content().getNeighbors(), not(hasItem(equalTo(CyclonNeighbor.of(address3))))); // recipient should not be included

        assertThat(view.getNeighbors(), hasSize(3)); // oldest neighbor removed
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address0, 1)))); // incremented age
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address1, 2)))); // incremented age
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address2, 2)))); // incremented age
    }

    @Test
    void shouldUpdateOwnViewOnResponse(@Mock(name = "localAddress") final DrasylAddress localAddress,
                                       @Mock(name = "recipient") final DrasylAddress recipient,
                                       @Mock(name = "address0") final DrasylAddress address0,
                                       @Mock(name = "address1") final DrasylAddress address1,
                                       @Mock(name = "address2") final DrasylAddress address2,
                                       @Mock(name = "address3") final DrasylAddress address3,
                                       @Mock(name = "address4") final DrasylAddress address4,
                                       @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) throws Exception {
        // arrange
        when(ctx.channel().localAddress()).thenReturn(localAddress);
        final OverlayAddressedMessage<CyclonShuffleRequest> shuffleRequest = new OverlayAddressedMessage<>(CyclonShuffleRequest.of(
                CyclonNeighbor.of(localAddress, 0),
                CyclonNeighbor.of(address2, 2)
        ), recipient);
        final CyclonView view = CyclonView.of(4, Set.of(
                CyclonNeighbor.of(address0, 2),
                CyclonNeighbor.of(address1, 2),
                CyclonNeighbor.of(address2, 2),
                CyclonNeighbor.of(address3, 3)
        ));
        final CyclonShuffleResponse response = CyclonShuffleResponse.of(Set.of(
                CyclonNeighbor.of(localAddress, 0), // own address must be filtered out
                CyclonNeighbor.of(address4, 3) // should replace address2 contained in request
        ));

        // act
        final ChannelInboundHandler handler = new CyclonShufflingClientHandler(3, 100, view, shuffleRequest);
        handler.channelRead(ctx, new OverlayAddressedMessage<>(response, null, recipient));

        // assert
        assertThat(view.getNeighbors(), hasSize(4));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address0, 2))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address1, 2))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address4, 3))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address3, 3))));
    }
}
