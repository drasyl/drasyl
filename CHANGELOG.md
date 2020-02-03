# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.0] - 2020-01-21

### Upgrade notice
- In this version there were multiple breaking chances. Many models where renamed.

### Added
- Added PingPongHandler - the client/peer must now response with a PongMessage to a PingMessage
- Added PingMessage
- Added PongMessage
- Added multiple default Handler for netty in the common package
- Added MessageDecoder/MessageEncoder for netty pipeline in common package
- Added OutboundConnectionFactory for outbound connections from relay->relay and client->relay
- Added `relay.monitoring.websocket_path` to reference.conf
- Added `relay.flush_buffer_size` to reference.conf
- Added `relay.ssl.enabled` to reference.conf
- Added `relay.ssl.protocols` to reference.conf
- Added `relay.idle.timeout` to reference.conf
- Added `relay.idle.retries` to reference.conf
- Added `relay.monitoring.dead_clients_saving_time` to reference.conf
- Added multicast support for SessionUIDs

### Changed
- Replaced Java IO with netty as NIO implementation
- Replaced Java Spark with netty.
- Changed from HTTP to Websocket for monitoring page
- Renamed AkkaId to SessionUID
- Replaced DummyClient with TestSession (also NIO)
- Changed syntax of SessionUID
- Changed random SessionUID generator, no longer UUID
- Renamed Channel model to SessionChannel to avoid clashes with netty's Channel

### Removed
- Removed ActorURI
- Removed DummyClient
- Temporary removed relay P2P

## [1.4.1-SNAPSHOT] - 2020-01-05

### Upgrade notice
- If you want a custom User-Agent string, you must override the static `Message.userAgentGenerator` field. It is recommended to append your custom User-Agent string to the output of the `Message.defaultUserAgentGenerator`. E.g.:

```java
Message.userAgentGenerator = () -> Message.defaultUserAgentGenerator.get() + " "
	+ config.getString("relay.user-agent");
```

- Also remember to adapt to the new Channel object.

### Added
- Added response Response with Status.OK on incomming ExceptionMessages
- Added response Response with Status.OK on incomming LeaveMessages
- Added response Response with Status.OK on incomming AkkaSystemJoinedMessages
- Added response Response with Status.OK on incomming AkkaSystemLeaveMessages
- Added response Response with Status.OK on incomming ForwardableP2PMessages
- Added response Response with Status.OK on incomming InitCompleteMessages
- Added response Response with Status.OK on incomming PeerOfflineMessages
- Added response Response with Status.NOT_IMPLEMENTED on incomming HandoffCompletedMessages
- Added `relay.version` to reference.conf
- Added `relay.user-agent` to reference.conf
- Added `common.version` to reference.conf
- Added `common.user-agent` to reference.conf
- Added `user_agent` field to Message
- Added Channel model
- Added to UI an overview for total sent messages (successful/failed)
- Added to UI a tab with dead connections (preserve information's for n seconds)

### Changed
- Changed the response by an already open connection from ExceptionMessage to a Response with an ExceptionMessage
- Removed the LeaveMessage from the NetworkTool.alive() method, because this is no longer needed (the relay recognizes the broken connection)
- Corrected the JavaDoc of the Client
- Changed every use of sessionChannel as String to the new Channel object
- Changed UI: server config and connections in separate tab
- Changed UI: table to 100% width
- Changed UI: properties of a client are side by side

## [1.4.0-SNAPSHOT] - 2019-12-27

### Upgrade notice
The new fields in the reference.conf has to be added to all existing configuration files.

### Added
- Added monitoring API
- Added monitoring UI
- Added `monitoring.enabled` to reference.conf
- Added `monitoring.token` to reference.conf
- Added `monitoring.port` to reference.conf
- Added optional `debugging` to reference.conf

### Changed
- Replaced deprecated mockito methods
