# Transport Channels

On this page the different [P2PTransportChannels](akka-p2p/src/main/java/city/sane/akka/p2p/P2PTransportChannel.java) are presented.

## Relay Server Transport Channel

The [RelayP2PTransportChannel](akka-p2p/src/main/java/city/sane/akka/p2p/relay/RelayP2PTransportChannel.java) provides a permanent
connection to a central exposed relay server. All messages are sent to this server, which then forwards them to the respective
recipient. This allows communication between systems that cannot reach each other directly