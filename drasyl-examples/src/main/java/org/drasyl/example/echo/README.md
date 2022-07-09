# Echo Protocol

This example implements the [Echo Protocol](https://tools.ietf.org/html/rfc862). An echo server
simply sends back to the originating source any data it receives.

This example consists of an [echo client](EchoClient.java) and two available echo server implementations ([1](EchoServerNode.java) and [2](EchoServerBootstrap.java))
sending the same message back and forth all the time.

The first server implementation is based on the simple [DrasylNode](../../../../../../../../drasyl-node/src/main/java/org/drasyl/node/DrasylNode.java) interface.
The second server implementation uses a more extensive interface with more flexibility.

## Usage

1. Start server: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.echo.EchoServerNode"` **OR** `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.echo.EchoServerBootstrap"`
3. Start
   client: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.echo.EchoClient" -Dexec.args="<server_address>"`
