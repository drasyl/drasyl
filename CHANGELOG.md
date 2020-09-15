# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2020-09-15
### Added
- Plugins can now be defined in the configuration `drasyl.plugins`. Plugins can be used to change the behavior of the node when interacting with the network.
- `DrasylNode.send()` can now send arbitrary objects (make sure that a suitable serialization codec is defined in the configuration `drasyl.marshalling`).
- `DrasylNode.identity()` returns own identity.
- Node will now try to expose itself via UPnP-IGD/NAT-PMP/PCP (can be disabled in the configuration via `drasyl.expose.enabled = false`).
- Local Host Discovery: Node can use the file system to discover other drasyl nodes running on the local computer (`drasyl.local-host-discovery`).
- CLI: `wormhole` sub-command added.

### Changed
- `wss://production.env.drasyl.org#025fff6f625f5dee816d9f8fe43895479aecfda187cb6a3330894a07e698bc5bd8` is now the default super peer (`drasyl.super-peer.endpoints`).
- Configuration `drasyl.super-peer.public-key` removed (public key can now be specified in configuration `drasyl.super-peer.endpoints`).
- `DrasylNode.send()` now returns a future, which complements when the message has been processed.
- `DrasylNode.send()` no longer throws exceptions, but writes them to the future.
- `DrasylNode.send()` is no longer synchronized.
- The own identity is now created in the `DrasylNode` constructor and not at node start.
- The `DrasylNode` constructor now throws an error if the own identity is invalid.
- Number of threads required by drasyl reduced by internal switch to `DrasylScheduler`.
- Chat example and CLI now stops the JVM after shutting down the node.
- Javadoc improved.
- Changed to a non-blocking logger.
- Chunking is now disabled by default in the configuration, because it is not fully implemented yet (`drasyl.message.max-content-length`).
- The drasyl thread pools are now lazily created when the first node is started.
- `MessageEvent.getMessage()` replaced by `MessageEvent.getSender()` and `MessageEvent.getPayload()`.
- Messages and Events objects are now immutable.
- Non-started node now throws a `NoPathToPublicKeyException` for all outgoing messages.
- Removed `PeerUnreachableEvent` and `NodeIdentityCollisionEvent`.

### Fixed
- Fixed concurrent modification error in `PeersManager`.
- Fixed concurrent modification error in `DirectConnectionsManager`.
- Fixed a bug that causes endless loops when a wanted handler was not the next handler in the pipeline.
- Fixed a bug that causes a `NullPointerException` when the node is started and stopped too quickly.
- Fixed a bug that results in a partially started node if it was started and stopped too quickly.
- Make sure that monitoring is also shut down in case of an error.
- Reduce time till sending ping messages to prevent problems with too aggressive reverse proxies (I mean you, nginx).

## [0.1.2] - 2020-07-14
### Added
- Release for Maven Central

## [0.1.1] - 2020-07-14
### Added
- Release for Maven Central

## [0.1.0] - 2020-07-10
### Added
- First release