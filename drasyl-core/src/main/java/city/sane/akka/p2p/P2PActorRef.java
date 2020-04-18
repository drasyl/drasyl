package city.sane.akka.p2p;

import akka.actor.*;
import akka.dispatch.sysmsg.Recreate;
import akka.dispatch.sysmsg.Resume;
import akka.dispatch.sysmsg.Suspend;
import akka.dispatch.sysmsg.SystemMessage;
import akka.dispatch.sysmsg.Terminate;
import city.sane.akka.p2p.transport.P2PTransport;
import org.jetbrains.annotations.Nullable;
import scala.collection.Iterator;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 *  Analog to: RemoteActorRef:
 *  P2P ActorRef that is used when referencing the Actor on a different node than its "home" node.
 *  This reference is network-aware (remembers its origin) and immutable.
 */
public class P2PActorRef extends InternalActorRef implements ActorRefScope {
    private final P2PTransport transport;
    private final Address localAddressToUse;
    private final ActorPath path;
    private final Deploy deploy;
    private final Props props;
    private final InternalActorRef parent;

    public P2PActorRef(P2PTransport transport,
                       ActorPath path) {
        this(transport, path.address(), path, null, null, null);
    }

    public P2PActorRef(P2PTransport transport,
                       Address localAddressToUse,
                       ActorPath path,
                       @Nullable InternalActorRef parent,
                       @Nullable Props props,
                       @Nullable Deploy deploy) {
        this.transport = transport;
        this.localAddressToUse = localAddressToUse;
        // check path for 'bud' correct protocol
        this.path = path;

        this.parent = parent;
        this.deploy = deploy;
        this.props = props;
    }

    /*
     * Actor life-cycle management, invoked only internally (in response to user requests via ActorContext).
     */

    @Override
    public void start() {
        if (props != null && deploy != null) {
            // FIXME implement deployment
            transport.getProvider().useActorOnNode(this, props, deploy, getParent());
        }

    }

    @Override
    public void resume(Throwable causedByFailure) {
        sendSystemMessage(new Resume(causedByFailure));
    }

    @Override
    public void suspend() {
        sendSystemMessage(new Suspend());
    }

    @Override
    public void restart(Throwable cause) {
        sendSystemMessage(new Recreate(cause));
    }

    @Override
    public void stop() {
        sendSystemMessage(new Terminate());
    }

    @Override
    public void sendSystemMessage(SystemMessage message) {
        transport.send(message, Optional.of(noSender()), this);
    }

    /**
     * Get a reference to the actor ref provider which created this ref.
     */
    @Override
    public ActorRefProvider provider() {
        return transport.getProvider();
    }

    /**
     * Obtain parent of this ref; used by getChild for ".." paths.
     */
    @Override
    public InternalActorRef getParent() {
        return parent;
    }

    /**
     * Obtain ActorRef by possibly traversing the actor tree or looking it up at
     * some provider-specific location. This method shall return the end result,
     * i.e. not only the next step in the look-up; this will typically involve
     * recursive invocation. A path element of ".." signifies the parent, a
     * trailing "" element must be disregarded. If the requested path does not
     * exist, return Nobody.
     */
    @Override
    public InternalActorRef getChild(Iterator<String> name) {
        // FIXME not implemented
        return null;
    }

    /**
     * Scope: if this ref points to an actor which resides within the same JVM,
     * i.e. whose mailbox is directly reachable etc.
     */
    @Override
    public boolean isLocal() {
//        String systemName = transport.getProvider().getSystemName();
//        return systemName.equals(path.address().system());
        return false;
    }

    @Override
    public ActorPath path() {
        return path;
    }

    /**
     * INTERNAL API: Returns “true” if the actor is locally known to be terminated, “false” if
     * alive or uncertain.
     */
    @Override
    public boolean isTerminated() {
        // FIXME: Implement
        return false;
    }

    @Override
    public void $bang(Object message, ActorRef sender) {
        if (message == null) {
            throw new InvalidMessageException("Message is null");
        }

        try {
            transport.send(message, Optional.ofNullable(sender), this);
        }
        catch (Exception e) {
            handleException(e, message, sender);
        }
    }

    private void handleException(Exception e, Object message, ActorRef sender) {
        throw new RuntimeException(e);
//        case e: InterruptedException =>
//        remote.system.eventStream.publish(Error(e, path.toString, getClass, "interrupted during message send"))
//        remote.system.deadLetters.tell(message, sender)
//        Thread.currentThread.interrupt()
//        case NonFatal(e) =>
//        remote.system.eventStream.publish(Error(e, path.toString, getClass, "swallowing exception during message send"))
//        remote.system.deadLetters.tell(message, sender)
    }

    public Address getLocalAddressToUse() {
        return localAddressToUse;
    }
}
