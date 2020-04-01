package org.drasyl.core.node;

import org.drasyl.core.models.Event;
import org.drasyl.core.models.Event.Node;
import org.drasyl.core.models.Identity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.core.models.Event.Code.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

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
    public void startShouldEmitOnlineEventOnSuccessfulSuperPeerRegistration() {
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
    public void startShouldEmitIdentityCollisionEventIfIdentityIsAlreadyUsedByAnotherNode() {
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
    public void startShouldNotEmitAnyEventsIfNodeHasAlreadyBeenStarted() {
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
    public void shutdownShouldEmitNormalTerminationEventOnSuccessfulSuperPeerDeregistration() {
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
    public void shutdownShouldEmitDeregisterFailedEventIfDeregistrationFromSuperPeerFailed() {
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
    public void shutdownShouldNotEmitAnyEventsIfNodeHasAlreadyBeenShutDown() {
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
    public void onEventShouldBeCalledForEveryEmittedEvent() {
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
    public void onMessageShouldBeCalledForEveryIncomingMessage() {
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
    public void sendShouldSendTheMessageToTheRecipient() {
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
    public void shouldEmitOfflineEventIfConnectionToSuperPeerIsLost() {
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
    public void shouldEmitOnlineEventIfBrokenConnectionToSuperPeerReestablished() {
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
    public void shouldEmitUnrecoverableErrorEventIfConnectionToSuperPeerCouldNotReestablished() {
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
