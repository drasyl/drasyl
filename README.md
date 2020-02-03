# drasyl Relay Server

# Latest Files

* [drasyl Relay Server](https://git.informatik.uni-hamburg.de/smartcity2019/relayserver/-/jobs/artifacts/master/file/server/target/relayserver-latest-full.zip?job=deploy)

# Test server
A test relay server is listening on `ws://relay1.incorum.org:22527/` as an WebSocket. If SSL is enabled, the relay is listening on `wss://relay1.incorum.org:22527/`.

# Monitoring
The monitoring website is by default deactivated and listening on port `8080`. Remember to change the
 `relay.monitoring.token` otherwise the relay server is **vulnerable**.

*You should also think about opening the port for monitoring only in the local area network or for a specific address range to prevent input fuzzing attacks.*

## Usage

To establish a connection to the relay, a [Join](all/src/main/java/org/drasyl/all/messages/Join.java) must be sent as a handshake.
If the client has been successfully registered with the relay, it responds with a [Welcome](all/src/main/java/org/drasyl/all/messages/Welcome.java) packed in a [Response](all/src/main/java/org/drasyl/all/messages/Response.java).
No messages from or to the client are forwarded before the handshake has taken place. 
**If a handshake is not successfully performed within 30s, the connection is disconnected. If a non-valid message is sent before a handshake, the connection is also closed because an incorrect protocol/client is assumed.**

See also this diagram:
![JoinMessageAction-sequence-diagram](https://git.informatik.uni-hamburg.de/smartcity2019/relayserver/raw/master/docs/diagrams/pdf/JoinMessageAction-sequence-diagram.pdf)


A dummy session can be found [here](integration-tests/src/test/java/city/sane/relay/server/testutils/TestSession.java).
How this is used can be seen in this [JUnit Integration Test](integration-tests/src/test/java/city/sane/relay/server/session/ClientIT.java).

Messages sent to the relay must always be encoded as JSON. The **common** submodule provides all known 
[`common/messages`](https://git.informatik.uni-hamburg.de/smartcity2019/relayserver/tree/master/common/src/main/java/city/sane/relay/common/messages).
These messages can be out of the box de-/serialized with the `com.fasterxml.jackson.databind.ObjectMapper` from Jackson. 
How to use these messages can be seen in this [JUnit tests](https://git.informatik.uni-hamburg.de/smartcity2019/relayserver/tree/master/common/src/test/java/city/sane/relay/common).

## Start
If you start drasyl, we recommend to add the `--illegal-access=permit` argument, to prevent warnings in the log. E.g.:
```
java --illegal-access=permit -jar relayserver.jar
```

## Configuration
We use the [Lightbend configuration library](https://github.com/lightbend/config) to configure drasyl. drasyl provides a [reference.conf](server/src/main/resources/reference.conf), which can either be completely overwritten by an application.conf or you can overwrite single values by using the Java System Properties.

For example, to set your own application.conf:
```
java "-Dconfig.file=/path/to/application.conf" -jar relayserver.jar
```

Example for replacing individual values:
```
java "-Drelay.port=80" "-Drelay.UID=myUID" -jar relayserver.jar
```

## GitLab Maven repository
We're using the GitLab CI/CD to build and deploy our releases.

**Step 1.** You have to add the GitLab repository to your pom.xml file as a new repository.

```xml
<repositories>
    <repository>
        <id>gitlab-maven</id>
        <url>https://git.informatik.uni-hamburg.de/api/v4/groups/smartcity2019/-/packages/maven</url>
    </repository>
</repositories>
```

**Step 2.** Add the relayserver, the server or the common module to you pom.xml file as a new dependency.

```xml
<dependency>
    <groupId>city.sane.relay</groupId>
    <artifactId>relayserver</artifactId>
    <version>1.5.0</version>
</dependency>
```

**or**

```xml
<dependency>
    <groupId>city.sane.relay</groupId>
    <artifactId>server</artifactId>
    <version>1.5.0</version>
</dependency>
```

**or**

```xml
<dependency>
    <groupId>city.sane.relay</groupId>
    <artifactId>common</artifactId>
    <version>1.5.0</version>
</dependency>
```

*Note:* Remember to adapt the version to your needs. You can see all versions at https://git.informatik.uni-hamburg.de/smartcity2019/relayserver/-/packages

## Developers

### Release new version

```bash
rm -f release.properties
mvn clean -DskipTests -Darguments=-DskipTests release:prepare
```

**An additional call of `mvn release:perform` is not necessary!**

Wait for GitLab to finish build tasks.