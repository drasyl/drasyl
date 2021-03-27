# Echo Protocol

This example implements the [Echo Protocol](https://tools.ietf.org/html/rfc862). An echo server
simply sends back to the originating source any data it receives.

This example consists of an [echo client](EchoClient.java) and [echo server](EchoServer.java)
sending the same message back and forth all the time.

## Usage

1. Start server: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.echo.EchoServer"`
1. Start
   client: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.echo.EchoClient" -Dexec.args="<server_address>"`
