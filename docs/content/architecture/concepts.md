# Concepts

In order to allow easy integration with arbitrary applications, the Overlay Network has a minimalist design. The main task is to provide transport channels
between any nodes in the world. Exposed super peers are used to discover other nodes. If a direct connection between two nodes is not possible, the traffic is
forwarded via a super peer. Each node generates an identity at the first start, through which the node can be uniquely addressed.

The network gives no guarantee that messages sent will arrive, arrive in the correct order or arrive exactly once.

## Network Topology

At startup the drasyl node connects to an always-present root node (the super peer).
The super peer helps to discover other nodes and establish direct connections to other nodes.

## Peer Discovery

drasyl uses different methods to find other peers.
Peers running within a JVM are automatically discovered via shared memory.
Local peers running in other JVMs on the same computer are found via the file system.
Remote nodes are found using the Super Peer, which acts as a rendezvous server.

Nodes always try to communicate with each other via a direct connection.
If this is not possible, messages are relayed through the super peer.
Currently, direct connections are only possible for nodes within the same local network.
In a [future release](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/73), direct connections across networks are planned] using NAT traversal techniques.

## Addressing

Each node generates on the first start a unique identity consisting of a public/private key-pair and a proof of work (PoW).
The public key is used to address the node.
The PoW is required to prevent sybil attacks on the network by generating a large amount of identities.
Currently a cpu-hard PoW is used. For the [future](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/76) a change to a memory-hard PoW is planned.

## Cryptography

In a [future release](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/60), messages wil be end-to-end encrypted.

## BPMN Diagrams

We have created several BPMN diagrams for important components/processes within the overlay network

* [Node Lifecycle](https://cawemo.com/share/6bd4ccf2-7d15-493e-9b9a-5cd7041d34e1)
* [Register at Super Peer](https://cawemo.com/share/6bd4ccf2-7d15-493e-9b9a-5cd7041d34e1)
* [Send, Relay & Receive Message](https://cawemo.com/share/442a5a0e-a922-4dd3-920a-fa625c8e1fe5)
* [Establish Direct Connection (P2P)](https://cawemo.com/share/7c80ab60-da67-4438-bf75-e2c9c1c7e0fb)
