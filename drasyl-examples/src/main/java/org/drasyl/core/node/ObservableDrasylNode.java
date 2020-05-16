package org.drasyl.core.node;

import com.typesafe.config.Config;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.server.NodeServer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This particular Implementation of a drasyl Node shows exemplary how incoming Events can be
 * processed using the Observer Pattern.
 */
public class ObservableDrasylNode extends DrasylNode {
    private final Subject<Event> events;

    public ObservableDrasylNode() throws DrasylException {
        super();
        this.events = PublishSubject.create();
    }

    public ObservableDrasylNode(Config config) throws DrasylException {
        super(config);
        this.events = PublishSubject.create();
    }

    ObservableDrasylNode(DrasylNodeConfig config,
                         IdentityManager identityManager,
                         PeersManager peersManager,
                         Messenger messenger,
                         NodeServer server,
                         SuperPeerClient superPeerClient,
                         AtomicBoolean started,
                         CompletableFuture<Void> startSequence,
                         CompletableFuture<Void> shutdownSequence,
                         Subject<Event> events) {
        super(config, identityManager, peersManager, messenger, server, superPeerClient, started, startSequence, shutdownSequence);
        this.events = events;
    }

    @Override
    public void onEvent(Event event) {
        events.onNext(event);
    }

    public static void main(String[] args) throws DrasylException {
        ObservableDrasylNode node = new ObservableDrasylNode();
        node.events().subscribe(System.out::println);
        node.start().join();
    }

    public Observable<Event> events() {
        return events;
    }
}
