# Time Protocol

This example implements the [Time Protocol](https://tools.ietf.org/html/rfc868). This protocol
provides a site-independent, machine readable date and time. The Time service sends back to the
originating source the time in seconds since midnight on January first 1900.

This example consists of a [time client](TimeClient.java) requesting the time by sending an empty
message to a [time server](TimeServer.java).

## Usage

1. Start server: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.time.TimeServer"`
1. Start
   client: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.time.TimeClient" -Dexec.args="<server_address>"`
