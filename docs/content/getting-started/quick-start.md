# Quick Start

This guide describes the necessary steps to create your first drasyl node and how to integrate it into your application.

Once the node is set up, it and therefore your application can participate in the drasyl Overlay Network and communicate with other nodes and applications.

## Add Dependency

Maven:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-core</artifactId>
    <version>0.2.0</version>
</dependency>
```

Gradle:

```compile group: 'org.drasyl', name: 'drasyl-core', version: '0.2.0'```

## Implementing `DrasylNode`

Next, you can create your own drasyl node by implementing [`DrasylNode`](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-core/src/main/java/org/drasyl/DrasylNode.java).

This class contains the following methods that are now relevant for you:

* `send(...)`: allows your application to send arbitrary messages to other drasyl nodes.
* `onEvent(...)`: allows your application to react to certain events (e.g. process received messages). This method must be implemented.
* `start()`: starts the node, which will then automatically connect to the drasyl network.
* `shutdown()`: disconnects from the drasyl network and shuts down the node.
 
Here you can see a minimal working example of a node that forwards all received events to `System.out`:
```java
DrasylNode node = new DrasylNode() {
    @Override
    public void onEvent(Event event) {
        System.out.println("Event received: " + event);
    }
};
```

## Node Events

As mentioned before, different events are received by the application.
These provide information about the state of your node, received messages or connections to other nodes.
It is therefore important that you become familiar with the [definitions and implications](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-core/src/main/java/org/drasyl/event) of
the different event types.

For example, you should listen for `NodeOnlineEvent` before start sending messages, and pause when `NodeOfflineEvent` has been received.

!!! info "Advanced References"
    
    If you are interested in the life cycle of the individual events, you can find a state diagram [here](../../architecture/diagrams/#node-events).

## Sending Messages

Every message that is to be sent requires a recipient address.
Each drasyl node creates an identity at its first startup consisting of a cryptographic public-private key pair.
From the public key, a 10 hex digit address is derived, by which each node can be uniquely identified.
Currently, addresses of recipient nodes must be known, as drasyl currently has no function for querying available addresses.

The `send()` method needs the recipient as first argument and the message payload as second argument.

!!! info "Example"

    ```java
        node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World")
    ```

The method does not give any feedback on whether the message could be delivered. However, it can throw an exception if the local node has no connection to the
drasyl network.

## Receiving Messages

Each received message is announced as an [`MessageEvent`](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-core/src/main/java/org/drasyl/event/MessageEvent.java) to the application. The event contains a pair with sender and payload of the message.

Example:
```java
...
public void onEvent(Event event) {
    if (event instanceof MessageEvent) {
        Pair message = event.getMessage();
        System.out.println("Message received from " + message.getSender() + " with payload " + message.getPayload());
    }
}
...
```

## Starting & Stopping the drasyl Node

Before you can use the drasyl node, you must start it using `node.start()`.
For communication with other nodes in the local network, the node starts a server
listening on port 22527. Make sure that the port is available.
After the node has been successfully started, it emits an `NodeUpEvent` to the application.
Then, once it has successfully connected to the overlay network, an `NodeOnlineEvent` is emitted.

If the node is temporarily or permanently no longer needed, it can be shut down using `node.shutdown()`.
An `NodeDownEvent` is emitted immediately after this call. The application should now no longer attempt to send messages.
As soon as the connection to the drasyl network is terminated, an `NodeOfflineEvent` is emitted.
An `NodeNormalTerminationEvent` is created when the shutdown is done.

## Customize Configuration

drasyl is designed to work with zero-configuration. However, some use cases require configuration customization.

There are many ways to customize the drasyl (e.g. via environment variables, config file, start parameters, [`DrasylConfig.Builder`](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-core/src/main/java/org/drasyl/DrasylConfig.java) class) 

The following example shows the usage of the `DrasyConfig.Builder`:
```java
DrasylConfig config = DrasylConfig.newBuilder()
        .loglevel(Level.DEBUG)
        .serverEnabled(false)
        .build();
```

The created `DrasylConfig` object can then be passed to the `DrasylNode` constructor.

An overview of all available configuration parameters, their meaning and all default values can be found in the [`reference.conf`](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-core/src/main/resources/reference.conf) file
and/or at the [configuration](../../configuration/) section.

Further information can be taken directly from the configuration library that is used internally by drasyl: [https://github.com/lightbend/config](https://github.com/lightbend/config)
