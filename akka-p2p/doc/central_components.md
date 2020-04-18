# Central Components

## Actor Ref Provider

Akka-P2P is implemented as a so-called [ActorRefProvider](https://doc.akka.io/japi/akka/current/akka/actor/ActorRefProvider.html).
ActorRefProviders are used by Akka in order to create
[Actor References](https://doc.akka.io/docs/akka/current/general/addressing.html#what-is-an-actor-reference).
The References are used to address messages to Actors. The Provider is also responsible to deliver these messages to the recipient
address.

By default Akka uses the Local Provider (`akka.actor.provider = local`), which only allows communication with Actors on the
the same system. Additionally, there is a [Remote Provider](https://doc.akka.io/docs/akka/current/remoting.html)
(`akka.actor.provider = remote`), which uses TCP sockets and thus allow communication between different (remote) systems. To
do this, however, the Remote Provider must be able to directly connect to any other platform.

The [P2PActorRefProvider](akka-p2p/src/main/java/city/sane/akka/p2p/P2PActorRefProvider.java) overcomes this limitation by using a
number of extensible mechanisms (e.g. rendezvous servers, NAT traversal) to provide a fully meshed overlay network. This allows
actors to communicate across network boundaries and form an internet-wide decentralized application.

An actor system that uses the P2P Provider will start the P2PTransport component at startup.

## Transport

The [P2PTransport](akka-p2p/src/main/java/city/sane/akka/p2p/P2PActorRefProvider.java) component is responsible for making the
Actor System part of the overlay network, allowing the sending of messages within the network.

The component uses a number of so-called [Transport Channels](channels.md) to fulfill its task. Each of these Channels uses a
specific mechanism to provide communication channels for messages.

This component automatically selects the most favorable channel for sending a particular message.
If the Transport component fails to provide at least one working channel, the Actor System
is notified and asked to stop.
