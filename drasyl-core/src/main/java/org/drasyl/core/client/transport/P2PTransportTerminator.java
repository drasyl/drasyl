package org.drasyl.core.client.transport;

import akka.Done;
import akka.Done$;
import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SystemGuardian.RegisterTerminationHook$;
import akka.actor.SystemGuardian.TerminationHook$;
import akka.actor.SystemGuardian.TerminationHookDone$;
import akka.japi.pf.FI.Apply2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drasyl.core.client.transport.P2PTransportTerminator.TerminatorState.*;

/**
 * This actor is notified when the actor system is shut down. It then triggers the graceful shutdown of P2P transport.
 */
public class P2PTransportTerminator extends AbstractFSM<P2PTransportTerminator.TerminatorState, Object> {
    private static final Logger log = LoggerFactory.getLogger(P2PTransportTerminator.class);

    private final ActorRef systemGuardian;

    {
        startWith(Uninitialized, null);

        when(
                Uninitialized,
                matchEvent(Internals.class, new Apply2<>() {
                    @Override
                    public State<TerminatorState, Object> apply(Internals internals, Object o) {
                        systemGuardian.tell(RegisterTerminationHook$.MODULE$, self());
                        return goTo(Idle).using(internals);
                    }
                })
        );

        when(
                Idle,
                matchEvent(TerminationHook$.MODULE$.getClass(), (terminationHook, o) -> {
                    log.info("Shutting down P2PTransport.");
                    ((Internals) o).transport.shutdown().thenRun(() -> self().tell(Done$.MODULE$, self()));
                    return goTo(WaitTransportShutdown);
                })
        );

        when(
                WaitTransportShutdown,
                matchEvent(Done.class, new Apply2<>() {
                    @Override
                    public State<TerminatorState, Object> apply(Done done, Object o) {
                        log.info("P2PTransport shut down.");
                        systemGuardian.tell(TerminationHookDone$.MODULE$, self());
                        return stop();
                    }
                }).event(Status.Failure.class, (failure, o) -> {
                    log.error("Remoting shut down with error", failure.cause());
                    return stop();
                })
        );
    }

    private P2PTransportTerminator(ActorRef systemGuardian) {
        this.systemGuardian = systemGuardian;
    }

    public static Props props(ActorRef systemGuardian) {
        return Props.create(P2PTransportTerminator.class, () -> new P2PTransportTerminator(systemGuardian));
    }

    enum TerminatorState {
        Uninitialized,
        Idle,
        WaitTransportShutdown
    }

    public static class Internals {
        P2PTransport transport;

        public Internals(P2PTransport transport) {
            this.transport = transport;
        }
    }
}
