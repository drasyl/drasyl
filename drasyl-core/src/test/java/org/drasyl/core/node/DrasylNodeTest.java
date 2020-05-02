/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.node;

import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.models.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.drasyl.core.models.Code.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@Disabled("currently not yet implemented")
public class DrasylNodeTest {
    private Node node;
    private Event event;
    private byte[] message;
    private Identity recipient;

    @BeforeEach
    void setUp() {
        event = mock(Event.class);
        node = mock(Node.class);
        recipient = mock(Identity.class);
        message = new byte[]{ 0x4f };
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    public void startShouldEmitOnlineEventOnSuccessfulSuperPeerRegistration() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.start();

        verify(drasylNode).onEvent(new Event(NODE_ONLINE, node, null, new byte[]{}));
    }

    @Test
    public void startShouldEmitIdentityCollisionEventIfIdentityIsAlreadyUsedByAnotherNode() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.start();

        verify(drasylNode).onEvent(new Event(NODE_IDENTITY_COLLISION, node, null, new byte[]{}));
    }

    @Test
    public void startShouldNotEmitAnyEventsIfNodeHasAlreadyBeenStarted() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.start();

        verify(drasylNode, times(0)).onEvent(any());
    }

    @Test
    public void shutdownShouldEmitNormalTerminationEventOnSuccessfulSuperPeerDeregistration() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode).onEvent(new Event(NODE_NORMAL_TERMINATION, node, null, new byte[]{}));
    }

    @Test
    public void shutdownShouldEmitDeregisterFailedEventIfDeregistrationFromSuperPeerFailed() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode).onEvent(new Event(NODE_DEREGISTER_FAILED, node, null, new byte[]{}));
    }

    @Test
    public void shutdownShouldNotEmitAnyEventsIfNodeHasAlreadyBeenShutDown() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode, times(0)).onEvent(any());
    }

    @Test
    public void onEventShouldBeCalledForEveryEmittedEvent() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode).onEvent(event);
    }

    @Test
    public void onEventShouldEmitOfflineEventIfConnectionToSuperPeerIsLost() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(NODE_OFFLINE, node, null, new byte[]{}));
    }

    @Test
    public void onEventShouldEmitOnlineEventIfBrokenConnectionToSuperPeerReestablished() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(NODE_ONLINE, node, null, new byte[]{}));
    }

    @Test
    public void onEventShouldEmitUnrecoverableErrorEventIfConnectionToSuperPeerCouldNotReestablished() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(NODE_UNRECOVERABLE_ERROR, node, null, new byte[]{}));
    }

    @Test
    public void onMessageShouldBeCalledForEveryIncomingMessage() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(MESSAGE, null, null, message));
    }

    @Test
    public void sendShouldSendTheMessageToTheRecipient() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            public void onEvent(Event event) {
            }
        });
        drasylNode.send(recipient, message);

        fail("make sure that DrasylNode has processed the message correctly");
    }
}
