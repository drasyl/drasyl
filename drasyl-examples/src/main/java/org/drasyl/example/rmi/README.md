# Remote Message Invocation

...

## Usage

1. Start server: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.rmi.RmiServerNode"`
3. Start
   client: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.rmi.RmiClientNode" -Dexec.args="<server_address>"`
