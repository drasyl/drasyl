package org.drasyl.handler.membership.cyclon;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static test.util.EqualNeighborWithSameAge.equalNeighborWithSameAge;

@ExtendWith(MockitoExtension.class)
class CyclonActiveShufflingHandlerTest {
    @SuppressWarnings("unchecked")
    @Test
    void shouldCreateProperRequestToOldestNeighborAndUpdateOwnView(@Mock(name = "address0") final DrasylAddress address0,
                                                                   @Mock(name = "address1") final DrasylAddress address1,
                                                                   @Mock(name = "address2") final DrasylAddress address2,
                                                                   @Mock(name = "address3") final DrasylAddress address3) {
        // arrange
        final CyclonView view = new CyclonView(4, new SortedList<>(List.of(
                CyclonNeighbor.of(address0, 0),
                CyclonNeighbor.of(address1, 1),
                CyclonNeighbor.of(address2, 1),
                CyclonNeighbor.of(address3, 2)
        )));

        // act
        final ChannelHandler handler = new CyclonShufflingClientHandler(2, 100, view, null);
        final EmbeddedChannel ch = new EmbeddedChannel(handler);

        // assert
        final AtomicReference<AddressedEnvelope<ShuffleRequest, ?>> requestRef = new AtomicReference<>();
        await().untilAsserted(() -> {
            ch.runScheduledPendingTasks();
            final Object o = ch.readOutbound();
            assertNotNull(o);
            assertThat(o, instanceOf(AddressedEnvelope.class));
            assertThat(((AddressedEnvelope<?, ?>) o).content(), instanceOf(ShuffleRequest.class));
            requestRef.set((AddressedEnvelope<ShuffleRequest, ?>) o);
        });
        final AddressedEnvelope<ShuffleRequest, ?> request = requestRef.get();
        assertEquals(address3, request.recipient());
        assertThat(request.content().getNeighbors(), hasSize(2)); // equal to shuffle size
        assertThat(request.content().getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of((DrasylAddress) ch.localAddress(), 0)))); // sender must be included
        assertThat(request.content().getNeighbors(), not(hasItem(equalTo(CyclonNeighbor.of(address3))))); // recipient should not included

        assertThat(view.getNeighbors(), hasSize(3)); // oldest neighbor removed
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address0, 1)))); // incremented age
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address1, 2)))); // incremented age
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address2, 2)))); // incremented age
    }

    @Test
    void shouldUpdateOwnViewOnResponse(@Mock(name = "recipient") final DrasylAddress recipient,
                                       @Mock(name = "address0") final DrasylAddress address0,
                                       @Mock(name = "address1") final DrasylAddress address1,
                                       @Mock(name = "address2") final DrasylAddress address2,
                                       @Mock(name = "address3") final DrasylAddress address3,
                                       @Mock(name = "address4") final DrasylAddress address4) {
        // arrange
        final EmbeddedChannel ch = new EmbeddedChannel();
        final OverlayAddressedMessage<ShuffleRequest> shuffleRequest = new OverlayAddressedMessage<>(ShuffleRequest.of(Set.of(
                CyclonNeighbor.of((DrasylAddress) ch.localAddress(), 0),
                CyclonNeighbor.of(address2, 2)
        )), recipient);
        final CyclonView view = new CyclonView(4, new SortedList<>(List.of(
                CyclonNeighbor.of(address0, 2),
                CyclonNeighbor.of(address1, 2),
                CyclonNeighbor.of(address2, 2),
                CyclonNeighbor.of(address3, 3)
        )));
        final ShuffleResponse response = ShuffleResponse.of(Set.of(
                CyclonNeighbor.of((DrasylAddress) ch.localAddress(), 0), // own address must be filtered out
                CyclonNeighbor.of(address4, 3) // should replace address2 contained in request
        ));

        // act
        final ChannelHandler handler = new CyclonShufflingClientHandler(3, 100, view, shuffleRequest);
        ch.pipeline().addLast(handler);

        ch.writeInbound(new DefaultAddressedEnvelope<>(response, null, recipient));

        // assert
        assertThat(view.getNeighbors(), hasSize(4));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address0, 2))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address1, 2))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address4, 3))));
        assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address3, 3))));
    }
}
