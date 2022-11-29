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

import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static test.util.EqualNeighborWithSameAge.equalNeighborWithSameAge;

@ExtendWith(MockitoExtension.class)
class CyclonViewTest {
    @Nested
    class Update {
        @Test
        void shouldUseEmptySlotsAndReplaceOldestReplaceCandidatesFirst(@Mock(name = "address0") final DrasylAddress address0,
                                                                       @Mock(name = "address1") final DrasylAddress address1,
                                                                       @Mock(name = "address2") final DrasylAddress address2,
                                                                       @Mock(name = "address3") final DrasylAddress address3,
                                                                       @Mock(name = "address4") final DrasylAddress address4) {
            // arrange
            final CyclonView view = CyclonView.of(4, Set.of(
                    CyclonNeighbor.of(address0, 1), // should be kept
                    CyclonNeighbor.of(address1, 2), // should be replaced (oldest replace candidate)
                    CyclonNeighbor.of(address2) // should be kept
            ));

            // act
            final Set<CyclonNeighbor> receivedNeighbors = Set.of(
                    CyclonNeighbor.of(address3),
                    CyclonNeighbor.of(address4)
            );
            final Set<CyclonNeighbor> replaceCandidates = Set.of(
                    CyclonNeighbor.of(address0, 1),
                    CyclonNeighbor.of(address1, 2)
            );
            view.update(receivedNeighbors, replaceCandidates);

            // assert
            assertThat(view.getNeighbors(), hasSize(4)); // equal to view size
            assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address0, 1))));
            assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address2, 0))));
            assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address3, 0))));
            assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address4, 0))));
        }

        @Test
        void shouldIgnoreReceivedNeighborsExceedingViewSizeAndReplacedOldestFirst(@Mock(name = "address0") final DrasylAddress address0,
                                                                                  @Mock(name = "address1") final DrasylAddress address1,
                                                                                  @Mock(name = "address2") final DrasylAddress address2,
                                                                                  @Mock(name = "address3") final DrasylAddress address3) {
            // arrange
            final CyclonView view = CyclonView.of(2, Set.of(
                    CyclonNeighbor.of(address0, 1), // should be replaced (replace candidate)
                    CyclonNeighbor.of(address1, 2)  // should be replaced (oldest neighbor)
            ));

            // act
            final Set<CyclonNeighbor> receivedNeighbors = Set.of(
                    CyclonNeighbor.of(address2, 1),
                    CyclonNeighbor.of(address3, 1)
            );
            final Set<CyclonNeighbor> replaceCandidates = Set.of(
                    CyclonNeighbor.of(address0, 1)
            );
            view.update(receivedNeighbors, replaceCandidates);

            // assert
            assertThat(view.getNeighbors(), hasSize(2)); // equal to view size
            assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address2, 1))));
            assertThat(view.getNeighbors(), hasItem(equalNeighborWithSameAge(CyclonNeighbor.of(address3, 1))));
        }
    }
}
