package org.drasyl.node;

import org.awaitility.Awaitility;
import org.drasyl.node.Event.Node;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.Collection;

import static org.awaitility.Awaitility.await;
import static org.drasyl.node.Event.Code.*;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class DrasylNodeTest {
    private Node node;

    @BeforeEach
    void setUp() {
        node = mock(Node.class);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    public void startShouldEmitOnlineEventOnSuccessfulSuperPeerRegistration() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };
        drasylNode.start();

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_ONLINE, node, null))));
    }

    @Test
    public void startShouldEmitIdentityCollisionEventIfIdentityIsAlreadyUsedByAnotherNode() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };
        drasylNode.start();

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_IDENTITY_COLLISION, node, null))));
    }

    @Test
    public void shutdownShouldEmitNormalTerminationEventOnSuccessfulSuperPeerDeregistration() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };
        drasylNode.shutdown();

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_NORMAL_TERMINATION, node, null))));
    }

    @Test
    public void shutdownShouldEmitDeregisterFailedEventIfDeregistrationFromSuperPeerFailed() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };
        drasylNode.shutdown();

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_DEREGISTER_FAILED, node, null))));
    }

    @Test
    public void shouldEmitOfflineEventIfConnectionToSuperPeerIsLost() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_OFFLINE, node, null))));
    }

    @Test
    public void shouldEmitOnlineEventIfBrokenConnectionToSuperPeerReestablished() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_ONLINE, node, null))));
    }

    @Test
    public void shouldEmitUnrecoverableErrorEventIfConnectionToSuperPeerCouldNotReestablished() {
        // FIXME: mock behaviour here

        Collection<Event> events = new ArrayList<>();
        DrasylNode drasylNode = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
            }

            @Override
            void onEvent(Event event) {
                events.add(event);
            }
        };

        await().untilAsserted(() -> assertThat(events, contains(new Event(NODE_UNRECOVERABLE_ERROR, node, null))));
    }
}
