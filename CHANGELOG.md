# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.0] - 2022-0X-XX

### Added

- It is now easier to create drasyl node through the bootstrap interface.
  Refer [our documentation](https://docs.drasyl.org/v0.9/advanced-usage/bootstrapping/) for more
  information.
- Added support for remote message invocations over drasyl.
  Refer [our documentation](https://docs.drasyl.org/v0.9/advanced-usage/remote-message-invocation/)
  for more information.
- Added support for decentralized membership managed using the CYCLON protocol.
  Refer [our documentation](https://docs.drasyl.org/v0.9/advanced-usage/membership-management/) for
  more information.
-
-
-

### Changed

- Dependencies have been updated.
- `DrasylNode` now provides guareented and in-order message delivery by default. Can be disabled
  through configuration parameter `drasyl.remote.message.arq.enabled`.
-
-
-

### Fixed

- CLI sub-command `tun`: Ensure that a route is fully removed upon request.
-
-

## [0.8.0] - 2022-07-10

### Added

- NAT Traversal is now also able to establish a P2P connection if both devices are behind a shared
  NAT device that does not support [hairpinning](https://datatracker.ietf.org/doc/html/rfc4787#section-6).
- Node: Added `UnconfirmedAddressResolveHandler` that does send messages to unconfirmed (sender)
  addresses as last-resort (before the messages were simply dropped).
- CLI: Option `--mtu` to sub-command `tun` added.
- CLI: Option `--rc-jsonrpc-tcp` and `--rc-jsonrpc-http` to sub-command `node` added.
- CLI: `node-rc` sub-command added.
- CLI: Option `--rc-jsonrpc-tcp` and `--rc-jsonrpc-http` to sub-command `tun` added.
- CLI: `tun-rc` sub-command added.
- CLI: `tun` command will not work within docker.

### Changed

- CLI: Improve `tun` sub-command performance by tweaking default MTU size.
- CLI: Instead of a random port, drasyl now listens on a port that is derived from the identity.
- Dependencies have been updated.

### Fixed

- Internet discovery now regularly checks if the super peer DNS records have changed.
- DrasylNode: Groups plugin is working again.
- [guava](https://github.com/google/guava) dependency removed.

## [0.7.0] - 2022-02-18

### Upgrade Notes

1. We did it again: The overlay protocol has been changed with breaking changes making it impossible
   to communicate with peers running older drasyl versions.
1. This version is introduces a new identity file format, every node will recreate a new identity on
   first start. If you would like to reuse your pre-existing identity, you have to migrate the file
   format by yourself. Asume you have the current file `drasyl.identity.json`:
```json
{
    "proofOfWork" : -2144920491,
    "identityKeyPair" : {
        "publicKey" : "feb2fa8a69e2a59ce7586349b8e8a44610d902ef2a30b1a46ebc5ff989813033",
        "secretKey" : "d716fdc9a164bea60a179eacf868416695ad8757035d0d83abd5a2b362bfa221feb2fa8a69e2a59ce7586349b8e8a44610d902ef2a30b1a46ebc5ff989813033"
    },
    "keyAgreementKeyPair" : {
        "publicKey" : "62c201382e37a50c3ed20f15ccb5ba970a264a2765f8f8c6f8e1c27b2454ca34",
        "secretKey" : "105672a9d44c3be1ba4b777f0c7d6cf711fb73defd404db7644f546ea71f0f5d"
    }
}
```
Then you need to create a **new** file `drasyl.identity` with the following format:
```ini
[Identity]
SecretKey = d716fdc9a164bea60a179eacf868416695ad8757035d0d83abd5a2b362bfa221feb2fa8a69e2a59ce7586349b8e8a44610d902ef2a30b1a46ebc5ff989813033
ProofOfWork = -2144920491
```

### Added

- CLI: `generate-identity` sub-command added.
- CLI: `generate-pow` sub-command added.
- CLI: `tun` sub-command added.
- CLI: `generate-completion` sub-command added.
- CLI: `--no-protocol-arming` option added.
- Broadcast-based LAN Discovery added. Can be used programatically. More
  information: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/merge_requests/680

### Changed

- Dependencies have been updated.
- Speed up direct connection establishment when traversing symmetrics NATs.
- Switch to more compact (INI-formatted) identity file format.
- CLI: `perf client`'s `--mps` option default value has changed to `0`. This causes the client to
  send messages as quickly as possible. to send messages as fast as possible.

### Fixed

- Fix endless identity creation, when none of the available nonces result in a valid identity.
- Fix IP multicast discovery for IPv6-only environments.
- Fix SSDP discovery for IPv6-only environments.
- Stop sending IP multicast messages to super peers when node is running in TCP-fallback mode.
- Fix problem with `null` message when using `BehavioralDrasylNode`.

## [0.6.0] - 2021-11-28

### Upgrade Notes

- We did it again: The overlay protocol has been changed with breaking changes making it impossible
  to communicate with peers running older drasyl versions.
- New Javadoc website: https://api.drasyl.org/

### Added

- [Netty](https://netty.io/) based channels are now used to process all overlay network I/O
  operations. This change allows you to use/add many netty handlers with the drasyl overlay.
- [`DrasylNode#resolve(...)`](./drasyl-node/src/main/java/org/drasyl/DrasylNode.java) will now
  return a dedicated [`Channel`](https://netty.io/4.1/api/io/netty/channel/Channel.html) for
  communication with the given peer.
- The above mentioned [`Channel`](https://netty.io/4.1/api/io/netty/channel/Channel.html) comes with
  a backpressure mechanism (`Channel#isWritable`/`Channel#bytesBeforeWritable`
  /`Channel#bytesBeforeUnwritable`) allowing the application to control how fast traffic is written
  to the overlay.
- The encryption of overlay management messages and application messages can now be disabled
  separately in
  the [config](drasyl-node/src/main/resources/reference.conf) (`drasyl.remote.message.arm.protocol.enabled`
  /`drasyl.remote.message.arm.application.enabled`).
- CLI: `wormhole` sub-command is now able to send files as well.
- CLI: `tunnel` sub-command added.

### Changed

- The classes `DrasylNode`, `DrasylConfig`, `DrasylException`, all `Event`s has been moved to the
  Maven module `org.drasyl:drasyl-node` and java packages `org.drasyl.node.*`.
- Dependencies have been updated.
- The monitoring feature was outdated/mostly unusable and has therefore been removed.
- Replaced protobuf with own message serialization allowing us to reduce the overlay overhead.

### Fixed

- Overwhelming application traffic will no longer cause the node to drop out of the overlay.

## [0.5.1] - 2021-09-14

### Changed

- Bump lazysodium-java from 5.0.1 to 5.1.1

### Fixed

- Fix visibility of InboundExceptionEvent#getError.
- Allow protobuf build on Apple Silicon and Linux ARM.
- Fix problem with lazysodium finding dependencies when drasyl is embedded in a fat jar.

## [0.5.0] - 2021-06-28

### Upgrade Notes

- The identities must be replaced by a new one. Just delete the old `drasyl.identity.json` and let
  drasyl generate a new one.
- If you're using a custom configuration with super peer defined, make sure to use (our) super peers
  running 0.5.0.

### Added

- Multicast is used to discovery other nodes running within the same network.
- TCP is used as fallback if UDP traffic is blocked.
- Experimental support for native image added.
- All traffic is now end-to-end encrypted 🎉.
- kqueue is used on macOS based systems for better performance.
- epoll is used on linux based systems for better performance.
- An `InboundExceptionEvent` is emitted every time an inbound message could not be processed.
- Support for Apple Silicon added.
- Backpressure mechanism for outbound messages added.

### Changed

- Switched to MIT License.
- `DrasylNode#send()` will now return an `CompletationStage` instead of an `CompletableFuture`.
- Dependencies have been updated.
- Maven module `parent` has been renamed to `drasyl-parent`.
- Class `CompressedPublicKey` has been renamed to `IdentityPublicKey`.

## [0.4.1] - 2021-03-11

### Changed

- Dependencies were updated.

### Fixed

- Fixed an NPE when requesting a port forwarding if the Internet Gateway Device responds with an
  error.
- Since the super peer sp-ham1 has connectivity problems, this has been replaced by the sp-fra1.
- Public and private keys are now displayed everywhere in the classic format.
- Unused dependencies removed.
- Some additional minor fixes.

## [0.4.0] - 2021-02-27

### Added

- Hole Punching is now used to traverse NATs. This should dramatically increase the proportion of
  direct connections between peers.
- Messages that are too large for a single UDP datagram are now automatically split into multiple
  datagrams (and re-assembled on the receiving side).
- When changing the network, new port mappings are now automatically created on the new network.
- `DrasylNode`'s API has been enhanced with `@NotNull` and `@Nullable` annotations.
- Messages that can be delivered within the same JVM are now passed by reference (previously these
  messages have been unnecessarily serialized).
  **So make sure you either send copies of your objects or it's fine for other nodes to make changes
  to that object.**
- Static routes to other remote peers can now be defined. This allows discovery to be omitted in
  static environments (or test setups).
- **Messages with content `null` are now possible.**
- `perf` command has been added to the CLI. This allows you to run performance tests of connections.
- Each node can now have several super peers simultaneously. The fastest super peer will
  automatically be used. If one or more super peers fail (temporarily), one of the remaining super
  peers will automatically be used. By default, each node will use two of our super peers.
- New examples have been added:
  https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example
- Nodes can now be implemented as finite state machines (see our example of the philosopher
  problem: https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example/diningphilosophers)
  .

### Changed

- By default, each node now listens on a port in the range 22528 and 65528, which is derived from
  its identity. This means that the chance for a port collision is now reduced when multiple nodes
  are running on one computer.
- UDP is now used instead of TCP for communication with remote peers.
- The entire protocol for constructing the overlay network has been revised (it's far better now!).
- Messages are now serialized in binary. This is faster and creates smaller messages on the wire.
- If possible, messages are processed using zero-copy.
- Documentation has been revised (Javadoc and/or documentation at https://docs.drasyl.org).
- Messages can now be additionally serialized by protobuf or Java. Furthermore, own serializers can
  be implemented. Read more at https://docs.drasyl.org.
- The third-party portmapper library has been replaced with our own more lightweight and more
  resilient implementation.
- All dependencies have been updated to the latest versions.

### Fixed

- `IntraVmDiscovery`: Other nodes running within the same JVM that belongs to a different overlay
  network are now ignored.
- `LocalHostDiscovery`: Other nodes running on the same local computer that belong to a different
  overlay network are now ignored.
- Content of received messages is now better verified before it is deserialized.
- Unchecked exceptions thrown by `DrasylNode.onEvent` no longer produce ridiculously long error
  messages.

## [0.3.0] - 2020-10-30

### Added

- Each node now belongs to a certain network ID. Connections between nodes will occur only if both.
  peers use the same network ID. The main network has ID 1 (see configuration `drasyl.network.id`).
- Added drasyl groups plugin for membership management.
- Added support for netty's Epoll channels and event loops.
- Plugins can now access the node's identity.

### Changed

- All `drasyl.marshalling` config properties will now distinct between allowed outgoing and ingoing
  types. Config must be updated according to our documentation.
- `drasyl-core` now only depends on slf4j and no longer on logback. As part of this, the methods.
  `DrasylNode.getLogLevel()` and `DrasylNode.setLogLevel()` have been removed and the configuration.
  `drasyl.loglevel` has been removed.
- Nodes now only accept children joins if they are configured as a
  super-peer (`drasyl.super-peer.enabled = false`).
- Removed grandchildren nodes. The hierarchy is now limited in depth.
- All-new plugin interface.
- Changed docker base image from `drasyl/drasyl-build-images:jre-11-curl-7.64.0`
  to `openjdk:11-jre-slim` and removed default HEALTHCHECK.
- Enhanced JavaDoc.
- Changed `TypeValidator` to distinguish between in- and outbound messages.
- EmbeddedPipeline was generalized to simplify handler testing.
- Node now checks if the permissions of the identity file are too open (POSIX file systems only).
- Changed `Pipeline.processInbound` to accept `RelayableMessage`.
- Endpoints defined in `drasyl.super-peer.endpoints` must now always include the super peer's public
  key.

### Fixed

- Fixed memory-leaks of certain immutable classes.
- Fixed `DefaultPipeline.addBefore` method.
- Fixed error in IntraVM discovery which has delivered events around the pipeline directly to the
  application.
- Several minor bug fixes.

## [0.2.0] - 2020-09-15

### Added

- Plugins can now be defined in the configuration `drasyl.plugins`. Plugins can be used to change
  the behavior of the node when interacting with the network.
- `DrasylNode.send()` can now send arbitrary objects (make sure that a suitable serialization codec
  is defined in the configuration `drasyl.marshalling`).
- `DrasylNode.identity()` returns own identity.
- Node will now try to expose itself via UPnP-IGD/NAT-PMP/PCP (can be disabled in the configuration
  via `drasyl.expose.enabled = false`).
- Local Host Discovery: Node can use the file system to discover other drasyl nodes running on the
  local computer (`drasyl.local-host-discovery`).
- CLI: `wormhole` sub-command added.

### Changed

- `wss://production.env.drasyl.org#025fff6f625f5dee816d9f8fe43895479aecfda187cb6a3330894a07e698bc5bd8`
  is now the default super peer (`drasyl.super-peer.endpoints`).
- Configuration `drasyl.super-peer.public-key` removed (public key can now be specified in
  configuration `drasyl.super-peer.endpoints`).
- `DrasylNode.send()` now returns a future, which complements when the message has been processed.
- `DrasylNode.send()` no longer throws exceptions, but writes them to the future.
- `DrasylNode.send()` is no longer synchronized.
- The own identity is now created in the `DrasylNode` constructor and not at node start.
- The `DrasylNode` constructor now throws an error if the own identity is invalid.
- Number of threads required by drasyl reduced by internal switch to `DrasylScheduler`.
- Chat example and CLI now stops the JVM after shutting down the node.
- Javadoc improved.
- Changed to a non-blocking logger.
- Chunking is now disabled by default in the configuration, because it is not fully implemented
  yet (`drasyl.message.max-content-length`).
- The drasyl thread pools are now lazily created when the first node is started.
- `MessageEvent.getMessage()` replaced by `MessageEvent.getSender()` and `MessageEvent.getPayload()`
  .
- Messages and Events objects are now immutable.
- Non-started node now throws a `NoPathToPublicKeyException` for all outgoing messages.
- Removed `PeerUnreachableEvent` and `NodeIdentityCollisionEvent`.

### Fixed

- Fixed concurrent modification error in `PeersManager`.
- Fixed concurrent modification error in `DirectConnectionsManager`.
- Fixed a bug that causes endless loops when a wanted handler was not the next handler in the
  pipeline.
- Fixed a bug that causes a `NullPointerException` when the node is started and stopped too quickly.
- Fixed a bug that results in a partially started node if it was started and stopped too quickly.
- Make sure that monitoring is also shut down in case of an error.
- Reduce time till sending ping messages to prevent problems with too aggressive reverse proxies (I
  mean you, nginx).

## [0.1.2] - 2020-07-14

### Added

- Release for Maven Central

## [0.1.1] - 2020-07-14

### Added

- Release for Maven Central

## [0.1.0] - 2020-07-10

### Added

- First release
