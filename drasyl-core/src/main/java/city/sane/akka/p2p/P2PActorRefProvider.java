package city.sane.akka.p2p;

import akka.ConfigurationException;
import akka.actor.*;
import akka.event.EventStream;
import akka.event.MarkerLoggingAdapter;
import akka.japi.Function;
import akka.serialization.Serialization;
import city.sane.akka.p2p.transport.P2PTransport;
import city.sane.akka.p2p.transport.P2PTransportTerminator;
import city.sane.akka.p2p.transport.P2PTransportTerminator.Internals;
import scala.*;
import scala.collection.Iterable;
import scala.collection.JavaConverters;
import scala.collection.immutable.List;
import scala.collection.immutable.Nil$;
import scala.concurrent.Future;

/**
 * Central component of Akka-P2P. Provides the link between the Actor System and Akka-P2P.
 */
public class P2PActorRefProvider implements ActorRefProvider {
    public final static String SCHEME = "bud";

    private final ActorSystem.Settings settings;
    private final LocalActorRefProvider local;
    private final P2PDeployer deployer;
    private final String systemName;
    private P2PTransport transport;
    private ActorRef transportTerminator;

    public P2PActorRefProvider(String systemName,
                               ActorSystem.Settings settings,
                               EventStream eventStream,
                               DynamicAccess dynamicAccess) {
        this.systemName = systemName;
        this.settings = settings;

        this.deployer = new P2PDeployer(settings, dynamicAccess);

        Function1<ActorPath, InternalActorRef> deadLetters = deadLettersPath -> new DeadLetterActorRef(this, deadLettersPath, eventStream);
        this.local = new LocalActorRefProvider(systemName, settings, eventStream, dynamicAccess, deployer, Option.apply(deadLetters));
    }

    /**
     * Reference to the supervisor of guardian and systemGuardian; this is
     * exposed so that the ActorSystemImpl can use it as lookupRoot, i.e.
     * for anchoring absolute actor look-ups.
     *
     * @return
     */
    @Override
    public InternalActorRef rootGuardian() {
        return local.rootGuardian();
    }

    /**
     * Reference to the supervisor of guardian and systemGuardian at the specified address;
     * this is exposed so that the ActorRefFactory can use it as lookupRoot, i.e.
     * for anchoring absolute actor selections.
     */
    @Override
    public ActorRef rootGuardianAt(Address address) {
        if (hasAddress(address)) {
            // local
            return rootGuardian();
        }
        else {
            // remote
            return new P2PActorRef(
                    transport,
                    transport.localAddressForRemote(address),
                    new RootActorPath(address, "/"),
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * Reference to the supervisor used for all top-level user actors.
     */
    @Override
    public LocalActorRef guardian() {
        return local.guardian();
    }

    /**
     * Reference to the supervisor used for all top-level system actors.
     */
    @Override
    public LocalActorRef systemGuardian() {
        return local.systemGuardian();
    }

    /**
     * Dead letter destination for this provider.
     *
     * @return
     */
    @Override
    public InternalActorRef deadLetters() {
        return local.deadLetters();
    }

    /**
     * The root path for all actors within this actor system, not including any remote address information.
     */
    @Override
    public ActorPath rootPath() {
        return local.rootPath();
    }

    /**
     * The Settings associated with this ActorRefProvider
     */
    @Override
    public ActorSystem.Settings settings() {
        return settings;
    }

    /**
     * INTERNAL API: Initialization of an ActorRefProvider happens in two steps: first
     * construction of the object with settings, eventStream, etc.
     * and then—when the ActorSystem is constructed—the second phase during
     * which actors may be created (e.g. the guardians).
     */
    @Override
    public void init(ActorSystemImpl system) {
        local.init(system);

        // create an actor that listens for the shutdown of the actor system. This allows us to gracefully shut down the P2P transport
        transportTerminator = system.systemActorOf(P2PTransportTerminator.props(local.systemGuardian()), "remoting-terminator");

        transport = new P2PTransport(system, this);

        transportTerminator.tell(new Internals(transport), ActorRef.noSender());

        transport.start();
    }

    /**
     * The Deployer associated with this ActorRefProvider
     */
    @Override
    public Deployer deployer() {
        return deployer;
    }

    /**
     * Generates and returns a unique actor path below “/temp”.
     */
    @Override
    public ActorPath tempPath() {
        return local.tempPath();
    }

    /**
     * Returns the actor reference representing the “/temp” path.
     *
     * @return
     */
    @Override
    public VirtualPathContainer tempContainer() {
        return local.tempContainer();
    }

    /**
     * INTERNAL API: Registers an actorRef at a path returned by tempPath(); do NOT pass in any other path.
     */
    @Override
    public void registerTempActor(InternalActorRef actorRef, ActorPath path) {
        local.registerTempActor(actorRef, path);
    }

    /**
     * Unregister a temporary actor from the “/temp” path (i.e. obtained from tempPath()); do NOT pass in any other path.
     */
    @Override
    public void unregisterTempActor(ActorPath path) {
        local.unregisterTempActor(path);
    }

    /**
     * INTERNAL API: Actor factory with create-only semantics: will create an actor as
     * described by props with the given supervisor and path (may be different
     * in case of remote supervision). If systemService is true, deployment is
     * bypassed (local-only). If ``Some(deploy)`` is passed in, it should be
     * regarded as taking precedence over the nominally applicable settings,
     * but it should be overridable from external configuration; the lookup of
     * the latter can be suppressed by setting ``lookupDeploy`` to ``false``.
     */
    @Override
    public InternalActorRef actorOf(ActorSystemImpl system,
                                    Props props,
                                    InternalActorRef supervisor,
                                    ActorPath path,
                                    boolean systemService,
                                    Option<Deploy> deploy,
                                    boolean lookupDeploy,
                                    boolean async) {
        if (systemService) {
            return local.actorOf(system, props, supervisor, path, systemService, deploy, lookupDeploy, async);
        }
        else {
            if (!system.dispatchers().hasDispatcher(props.dispatcher())) {
                throw new ConfigurationException("Dispatcher [" + props.dispatcher() + "] not configured for path " + path);
            }

            Function<scala.collection.immutable.Iterable<String>, Option<Deploy>> lookupRemotes = null;

            scala.collection.immutable.Iterable<String> elems = path.elements();
            Option<Deploy> lookup;
            if (lookupDeploy) {
                switch (elems.head()) {
                    case "user":
                    case "system":
                        lookup = deployer.lookup(elems.drop(1));
                        break;

                    case "remote":
                        try {
                            lookup = lookupRemotes.apply(elems);
                        }
                        catch (Exception e) {
                            lookup = Option$.MODULE$.apply(null);
                        }
                        break;

                    default:
                        lookup = Option$.MODULE$.apply(null);
                }
            }
            else {
                lookup = Option$.MODULE$.apply(null);
            }

            List<Deploy> l = deploy.toList().$colon$colon$colon(lookup.toList());
            List<Deploy> deployment;
            if (l.isEmpty()) {
                deployment = Nil$.empty();
            }
            else {
                deployment = JavaConverters.asScalaBuffer(java.util.List.of(l.reduce((Function2<Deploy, Deploy, Deploy>) (a, b) -> b.withFallback(a)))).toList();
            }

//            (Iterator(props.deploy) ++ deployment.iterator).reduce((a, b) => b.withFallback(a)) match {
//                case d @ Deploy(_, _, _, RemoteScope(address), _, _) =>
//                    if (hasAddress(address)) {
//                        local.actorOf(system, props, supervisor, path, false, deployment.headOption, false, async)
//                    } else if (props.deploy.scope == LocalScope) {
//                        throw new ConfigurationException(
//                                s"${ErrorMessages.RemoteDeploymentConfigErrorPrefix} for local-only Props at [$path]")
//                    } else
//                        try {
//                            try {
//                                // for consistency we check configuration of dispatcher and mailbox locally
//                                val dispatcher = system.dispatchers.lookup(props.dispatcher)
//                                system.mailboxes.getMailboxType(props, dispatcher.configurator.config)
//                            } catch {
//                                case NonFatal(e) =>
//                                    throw new ConfigurationException(
//                                            s"configuration problem while creating [$path] with dispatcher [${props.dispatcher}] and mailbox [${props.mailbox}]",
//                                            e)
//                            }
//                            val localAddress = transport.localAddressForRemote(address)
//                            val rpath =
//                                    (RootActorPath(address) / "remote" / localAddress.protocol / localAddress.hostPort / path.elements)
//                                            .withUid(path.uid)
//                            new RemoteActorRef(transport, localAddress, rpath, supervisor, Some(props), Some(d))
//                        } catch {
//                    case NonFatal(e) => throw new IllegalArgumentException(s"remote deployment failed for [$path]", e)
//                }
//
//                case _ =>
            return local.actorOf(system, props, supervisor, path, systemService, deployment.headOption(), false, async);
//            }
        }
    }

    /**
     * INTERNAL API
     * <p>
     * Create actor reference for a specified local or remote path. If no such
     * actor exists, it will be (equivalent to) a dead letter reference.
     * <p>
     * Actor references acquired with `actorFor` do not always include the full information
     * about the underlying actor identity and therefore such references do not always compare
     * equal to references acquired with `actorOf`, `sender`, or `context.self`.
     */
    @Override
    @Deprecated
    public InternalActorRef actorFor(ActorPath path) {
        if (hasAddress(path.address())) {
            return actorFor(rootGuardian(), path.elements());
        }
        else {
            return new P2PActorRef(
                    transport,
                    transport.localAddressForRemote(path.address()),
                    path,
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * INTERNAL API
     * <p>
     * Create actor reference for a specified local or remote path, which will
     * be parsed using java.net.URI. If no such actor exists, it will be
     * (equivalent to) a dead letter reference. If `s` is a relative URI, resolve
     * it relative to the given ref.
     */
    @Override
    @Deprecated
    public InternalActorRef actorFor(InternalActorRef ref, String path) {
        Option<Tuple2<Address, scala.collection.immutable.Iterable<String>>> unapply = ActorPathExtractor.unapply(path);
        if (!unapply.isEmpty()) {
            Tuple2<Address, scala.collection.immutable.Iterable<String>> tuple2 = unapply.get();
            Address address = tuple2._1();
            scala.collection.immutable.Iterable<String> elems = tuple2._2();

            if (hasAddress(address)) {
                return actorFor(rootGuardian(), elems);
            }
            else {
                ActorPath rootPath = RootActorPath.apply(address, "/").$div(elems);
                return new P2PActorRef(
                        transport,
                        transport.localAddressForRemote(address),
                        rootPath,
                        null,
                        null,
                        null);
            }
        }
        else {
            return local.actorFor(ref, path);
        }
    }

    /**
     * INTERNAL API
     * <p>
     * Create actor reference for the specified child path starting at the
     * given starting point. This method always returns an actor which is “logically local”,
     * i.e. it cannot be used to obtain a reference to an actor which is not
     * physically or logically attached to this actor system.
     */
    @Override
    @Deprecated
    public InternalActorRef actorFor(InternalActorRef ref, Iterable<String> path) {
        return local.actorFor(ref, path);
    }

    /**
     * Create actor reference for a specified path. If no such
     * actor exists, it will be (equivalent to) a dead letter reference.
     */
    @Override
    public ActorRef resolveActorRef(String path) {
        Option<Tuple2<Address, scala.collection.immutable.Iterable<String>>> unapply = ActorPathExtractor.unapply(path);
        if (!unapply.isEmpty()) {
            Tuple2<Address, scala.collection.immutable.Iterable<String>> tuple2 = unapply.get();
            Address address = tuple2._1();
            scala.collection.immutable.Iterable<String> elems = tuple2._2();

            if (hasAddress(address)) {
                return local.resolveActorRef(rootGuardian(), elems);
            }
            else {
                ActorPath rootPath = RootActorPath.apply(address, "/").$div(elems);
                return new P2PActorRef(
                        transport,
                        transport.localAddressForRemote(address),
                        rootPath,
                        null,
                        null,
                        null);
            }
        }
        else {
            log().debug("Resolve (deserialization) of unknown (invalid) path [{}], using deadLetters.", path);
            return deadLetters();
        }
    }

    /**
     * Create actor reference for a specified path. If no such
     * actor exists, it will be (equivalent to) a dead letter reference.
     */
    @Override
    public ActorRef resolveActorRef(ActorPath path) {
        // Check if local system
        if (hasAddress(path.address())) {
            return local.resolveActorRef(rootGuardian(), path.elements());
        }
        else {
            return new P2PActorRef(transport, path);
        }
    }

    /**
     * This Future is completed upon termination of this ActorRefProvider, which
     * is usually initiated by stopping the guardian via ActorSystem.stop().
     */
    @Override
    public Future<Terminated> terminationFuture() {
        return local.terminationFuture();
    }

    /**
     * Obtain the address which is to be used within sender references when
     * sending to the given other address or none if the other address cannot be
     * reached from this system (i.e. no means of communication known; no
     * attempt is made to verify actual reachability).
     */
    @Override
    public Option<Address> getExternalAddressFor(Address addr) {
        return local.getExternalAddressFor(addr);
        // FIXME: Implement!
    }

    /**
     * Obtain the external address of the default transport.
     */
    @Override
    public Address getDefaultAddress() {
        return transport.defaultAddress();
    }

    @Override
    public Serialization.Information serializationInformation() {
        return local.serializationInformation();
        // We might have to define a Serialization method here. Could it be possible to use different serializers
        // on different systems? They should not be able to communicate like that. => Define Jackson as standard
        // FIXME: Implement!
    }

    /**
     * Returns <code>true</code>, if this provider is has <code>address</code>.
     *
     * @param address
     *
     * @return
     */
    private boolean hasAddress(Address address) {
        return address.equals(local.rootPath().address()) || address.equals(rootPath().address()) || transport.addresses().contains(address);
    }

    public MarkerLoggingAdapter log() {
        return local.log();
    }

    public InternalActorRef resolveActorRefWithLocalAddress(String path, Address localAddress) {
        Option<Tuple2<Address, scala.collection.immutable.Iterable<String>>> unapply = ActorPathExtractor.unapply(path);
        if (!unapply.isEmpty()) {
            Tuple2<Address, scala.collection.immutable.Iterable<String>> tuple2 = unapply.get();
            Address address = tuple2._1();
            scala.collection.immutable.Iterable<String> elems = tuple2._2();

            if (hasAddress(address)) {
                return local.resolveActorRef(rootGuardian(), elems);
            }
            else {
                ActorPath rootPath = RootActorPath.apply(address, "/").$div(elems);
                return new P2PActorRef(
                        transport,
                        localAddress,
                        rootPath,
                        null,
                        null,
                        null);
            }
        }
        else {
            log().debug("Resolve (deserialization) of unknown (invalid) path [{}], using deadLetters.", path);
            return deadLetters();
        }
    }

    /**
     * Using (checking out) actor on a specific node.
     */
    public void useActorOnNode(P2PActorRef p2PActorRef, Props props, Deploy deploy, InternalActorRef supervisor) {

        // resolveActorRef ( with path...) -> dummy: local actor ref
        // tell dummy -> DeamonMsgCreate( props, deploy, ... )

        // use provider watcher to watch dummy !!
    }

    public String getSystemName() {
        return systemName;
    }
}
