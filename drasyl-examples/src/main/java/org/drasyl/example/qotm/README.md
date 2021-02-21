# drasyl as Quote of the Moment Server / Client

This example demonstrates how to implement the quote server / client example from Oracle in drasyl: [https://docs.oracle.com/javase/tutorial/networking/datagrams/clientServer.html](https://docs.oracle.com/javase/tutorial/networking/datagrams/clientServer.html)

Start the server by
typing `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.qotm.QuoteOfTheMomentServer"`.

Start the client by
typing `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.qotm.QuoteOfTheMomentClient" -Dexec.args="<server_address>"`.
