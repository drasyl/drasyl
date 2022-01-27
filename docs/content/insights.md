# Insights

We give some (more or less deeper) insights about how drasyl works on this site.

## Addressing

Each node generates on the first start a unique identity consisting of a public/private key-pair and a proof of work (PoW):

* The public key is used to address the node.
* Furthermore, both keys are used for a key agreement. These results in two session keys `tx` and `rx`, respectively, for encryption and decryption.
* The PoW is bound to the address and acts as a counter measurement for sybil/eclipse attacks by reducing the number of identities an attacker can generate within a given time.

## Network Topology

Currently, the overlay network consists of a group of always present well-known public super nodes and (possible private) client nodes registering with at least one super node.
Each super node maintains a registry of currently registered overlay nodes. Client nodes use this information to discover other peers.

In the future, each public client node should be able to promote itself to a super node dynamically.
Furthermore, all super nodes should maintain a shared registry.

## Peer Discovery

drasyl uses different methods to find other peers:

* Peers running within the same process (JVM instance) are eagerly discovered through a shared memory-based discovery mechanism.
* Peers running in different processes but on the same host are eagerly discovered through a file system-based discovery mechanism.
* Peers running within the same network are eagerly discovered by using an IP Multicast-based discovery mechanism.
* Peers located in other networks are lazily discovered through well-known public reachable so-called super nodes that maintain a registry with all overlay nodes and their respective IP addresses/ports.

## NAT Traversal

Communication between any two computers on the Internet is not always possible.
Most computers on the Internet are located behind a NAT, which makes them inaccessible from the Internet.
This makes it somewhat difficult to connect to these computers.
drasyl utilizes UDP hole punching together with port forwarding to traverse as many NATs as possible.
As long as neither of the peers is behind a NAT with endpoint-dependent mapping (also known as symmetric NAT or "hard" NAT) and port forwarding is not possible, our approach can establish a direct connection.
Our approach can be seen as an improved version of "Simple Hole-Punching" presented in [NATCracker: NAT Combinations Matter](https://doi.org/10.1109/ICCCN.2009.5235278):

* Peer `A` and peer `B` periodically register with server `S` to join and remain on the overlay network.
  * `S` thus learns the (possibly NAT-mapped) public endpoint (IP address and port) of `A` and `B`.
  * This traffic from `A` and `B` to `S` creates NAT mappings allowing `S` to reach both `A` and `B`.
* If peer `A` wants to communicate with `B`, a respective intention is sent to `S`.
* `S` then sends the known endpoint of `A` and `B` to the respective other peer.
* `A` and `B` now try to reach each other via the endpoint received from `S`.
* If one of the peers is behind an endpoint-dependent NAT, reachability messages may come from an endpoint distinct to the one received from `S`.
In this case, the peer switches to the newly discovered endpoint and tries to reach the peer at this endpoint.

Typically, the well-known Interactive Connectivity Establishment ([RFC 5245](https://datatracker.ietf.org/doc/html/rfc5245)) is used for NAT traversal. We decided against it for the following reasons:

* While ICE needs several seconds for traversal, our approach takes only a few tenths of a millisecond.
* ICE itself does not provide any security services (see Section 18 of RFC 5245) and relies on other protocols. This causes more handshakes and state exchange, which leads to increased traversal time and message complexity. In our approach, however, all communication is already authenticated and encrypted.

## Message Routing

drasyl routes messages automatically via the shortest available path (same process > same host > same network > traversed direct connection > relayed connection).
The overlay will never give up on discovering more-local paths.
If a node has no path to a peer, it will use one of its super nodes as a default gateway.
The super node will hopefully have a path to the recipient and will then relay the message.
Otherwise, the message will be discarded.
Once a super node relays a message it will help both peers to traverse any possible present NATs.

## Cryptography

!!! important
    Any message in drasyl is encrypted by default unless you disable the encryption in the configuration file. 
    We strongly advise against turning off the encryption!


drasyl uses the public part of an ed25519 key pair to address any node in the drasyl network. To provide fast 0-RTT encryption, 
drasyl operates in two modes of encryption:

1. In the first mode, drasyl uses the ed25519 keys and converts them into a curve25519, to do an
   0-RTT x25519 key agreement – we call this the long-time encryption. Long-time encryption is the
   default case.

2. To raise the security and provide perfect forward secrecy, drasyl tries in the background to do
   key exchange and agreement with a newly generated ephemeral curve25519. If a perfect forward
   secrecy session was established between the two nodes, drasyl fires
   a [PerfectForwardSecrecyEncryptionEvent](https://api.drasyl.org/master/org/drasyl/node/event/PerfectForwardSecrecyEncryptionEvent.html)
   . If the connection provides no longer perfect forward secrecy
   a [LongTimeEncryptionEvent](https://api.drasyl.org/master/org/drasyl/node/event/LongTimeEncryptionEvent.html)
   is fired.

Both x25519 key agreements generate two shared secrets, to avoid the need for any synchronization.

The shared secrets will be used as a key for the stream cipher XChaCha20, with the additional Poly1305 authenticator, 
to provide AEAD.
Using a stream cipher allows efficient computation on weak devices that do not have an AES hardware module.

## Protocol

drasyl uses a custom-tailored stateless protocol based on UDP. The following four message types exist:

* **Hello:** Sent from client nodes to super nodes for registration and between client nodes for NAT traversal (like a ping).
* **Ack:** Confirm the recieval and processing of an previously received *Hello* message (like a pong).
* **Unite:** Sent from super node to client nodes to provide routing information for NAT traversal.
* **App:** Carry user messages.

Each message type consists of an authenticated public header with routing information and an encrypted private body with the message payload.
Messages can only be authenticated and decrypted by the recipient.

To keep the protocol leightweight, no reliable transmission, error detection, or flow control of *App* messages is guaranteed.
Depending on the use case, these features can be provided by higher protocols.

