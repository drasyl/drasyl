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

## Addressing

Each node generates on the first start a unique identity consisting of a public/private key-pair and a proof of work (PoW).
The public key is used to address the node.
The PoW is required to prevent sybil attacks on the network by generating a large amount of identities.
Currently, a cpu-hard PoW is used. For the [future](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/76) a change to a memory-hard PoW is planned.

## Cryptography

!!! important
    Any message in drasyl is encrypted by default unless you disable the encryption in the configuration file. 
    We strongly advise against turning off the encryption!


drasyl uses the public part of an ed25519 key pair to address any node in the drasyl network. To provide fast 0-RTT encryption, 
drasyl operates in two modes of encryption:

1. In the first mode, drasyl uses the ed25519 keys and converts them into a curve25519, to do an 0-RTT x25519 key agreement – 
   we call this the long-time encryption. Long-time encryption is the default case.

2. To raise the security and provide perfect forward secrecy, drasyl tries in the background to do key exchange and agreement 
   with a newly generated ephemeral curve25519. If a perfect forward secrecy session was established between the two nodes, 
   drasyl fires a [PerfectForwardSecrecyEncryptionEvent](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/PerfectForwardSecrecyEncryptionEvent.html). 
   If the connection provides no longer perfect forward secrecy a [LongTimeEncryptionEvent](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/LongTimeEncryptionEvent.html) is fired.

Both x25519 key agreements generate two shared secrets, to avoid the need for any synchronization.

The shared secrets will be used as a key for the stream cipher XChaCha20, with the additional Poly1305 authenticator, 
to provide AEAD. Using a stream cipher allows efficient computation on weak devices that do not have an AES hardware module.

## BPMN Diagrams

We have created several BPMN diagrams for important components/processes within the overlay network

* [Node Lifecycle](https://cawemo.com/share/6bd4ccf2-7d15-493e-9b9a-5cd7041d34e1)
* [Register at Super Peer](https://cawemo.com/share/6bd4ccf2-7d15-493e-9b9a-5cd7041d34e1)
* [Send, Relay & Receive Message](https://cawemo.com/share/442a5a0e-a922-4dd3-920a-fa625c8e1fe5)
* [Establish Direct Connection (P2P)](https://cawemo.com/share/7c80ab60-da67-4438-bf75-e2c9c1c7e0fb)
