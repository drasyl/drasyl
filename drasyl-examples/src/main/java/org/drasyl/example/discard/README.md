# Discard Protocol

The most simplistic protocol in the world is not 'Hello, World!'
but [Discard](https://tools.ietf.org/html/rfc863). It's a protocol that discards any received data
without any response.

This example consists of a [discard client](DiscardClient.java) that sends random messages to
a [discard server](DiscardServer.java). The server discards all received messages.

## Usage

1. Start server: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.discard.DiscardServer"`
1. Start
   client: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.discard.DiscardClient" -Dexec.args="<server_address>"`
