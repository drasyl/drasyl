package org.drasyl.core.node;

import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.models.Node;
import org.drasyl.core.models.Identity;
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
    private byte[] payload;
    private Identity recipient;

    @BeforeEach
    void setUp() {
        event = mock(Event.class);
        node = mock(Node.class);
        recipient = mock(Identity.class);
        payload = new byte[]{ 0x4f };
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    public void startShouldEmitOnlineEventOnSuccessfulSuperPeerRegistration() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });
        drasylNode.start();

        verify(drasylNode).onEvent(new Event(NODE_ONLINE, node, null));
    }

    @Test
    public void startShouldEmitIdentityCollisionEventIfIdentityIsAlreadyUsedByAnotherNode() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });
        drasylNode.start();

        verify(drasylNode).onEvent(new Event(NODE_IDENTITY_COLLISION, node, null));
    }

    @Test
    public void startShouldNotEmitAnyEventsIfNodeHasAlreadyBeenStarted() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
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
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode).onEvent(new Event(NODE_NORMAL_TERMINATION, node, null));
    }

    @Test
    public void shutdownShouldEmitDeregisterFailedEventIfDeregistrationFromSuperPeerFailed() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode).onEvent(new Event(NODE_DEREGISTER_FAILED, node, null));
    }

    @Test
    public void shutdownShouldNotEmitAnyEventsIfNodeHasAlreadyBeenShutDown() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
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
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });
        drasylNode.shutdown();

        verify(drasylNode).onEvent(event);
    }

    @Test
    public void onMessageShouldBeCalledForEveryIncomingMessage() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });

        verify(drasylNode).onMessage(payload);
    }

    @Test
    public void sendShouldSendTheMessageToTheRecipient() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });
        drasylNode.send(recipient, payload);

        fail("make sure that DrasylNode has processed the message correctly");
    }

    @Test
    public void shouldEmitOfflineEventIfConnectionToSuperPeerIsLost() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(NODE_OFFLINE, node, null));
    }

    @Test
    public void shouldEmitOnlineEventIfBrokenConnectionToSuperPeerReestablished() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(NODE_ONLINE, node, null));
    }

    @Test
    public void shouldEmitUnrecoverableErrorEventIfConnectionToSuperPeerCouldNotReestablished() throws DrasylException {
        // FIXME: mock behaviour here

        DrasylNode drasylNode = spy(new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
            }
        });

        verify(drasylNode).onEvent(new Event(NODE_UNRECOVERABLE_ERROR, node, null));
    }
}
