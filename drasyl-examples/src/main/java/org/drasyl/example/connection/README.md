# Connection-Oriented Communication

This example implements the handlers in
the [`org.drasyl.handler.connection`](../../../../../../../../drasyl-core/src/main/java/org/drasyl/handler/connection)
package that are used to create connection-oriented communication to other peers. This can
useful, if state between both peers needs to be synchronized (like segment numbers/etc.).

This example consists of a [server](ConnectionServer.java) that awaits connections
from [clients](ConnectionClient.java).
On startup, the client initiates a handshake, at the end of which either a connection has been
established or failed.

## Usage

1. Start
   server: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.connection.ConnectionServer"`
1. Start
   client: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.connection.ConnectionClient" -Dexec.args="<server_address>"`
