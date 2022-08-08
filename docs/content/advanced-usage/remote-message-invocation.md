# Remote Message Invocation

This page shows you how to invoke remote objects served by other drasyl nodes.
This feature is similar to [Java Remote Message Invocation](https://en.wikipedia.org/wiki/Java_remote_method_invocation) but uses drasyl as the transport rather than TCP.

To use this feature, you have to use the [bootstrapping interface](./bootstrapping.md), where you have to customize the server channel's ChannelInitializer.

## Creating the Server

There are two steps needed to create a remote message invocation (RMI) server:

* Create an interface defining the client/server contract.
* Create an implementation of that interface.

### Defining the Contract

First of all, let's create the interface for the object want to invoke remotely.

As drasyl is asynchronous, each method declared in the interface must have the return
type [`Future`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html)
or `void`.

```java
public interface MessengerService {
    Future<String> sendMessage(String clientMessage);
}
```

Note, though, that drasyl supports the full Java specification for method signatures, as long as the Java
types are serializable by [Jackson](https://github.com/FasterXML/jackson-docs#tutorials). We'll see in
future sections how both the client and the server will use this interface. For the server, we'll
create the implementation, often referred to as the _Remote Object_. For the client, **the we will
dynamically create an implementation called a _Stub_**.

### Implementation

Furthermore, let's implement the remote interface, again called the _Remote Object_:

```java
public class MessengerServiceImpl implements MessengerService {
    @Override
    public Future<String> sendMessage(String clientMessage) {
        final String result = "Client Message".equals(clientMessage) ? "Server Message" : null;
        return new SucceededFuture<>(ImmediateEventExecutor.INSTANCE, result);
    }

    public String unexposedMethod() { /* code */ }
}
```

Notice that any additional methods defined in the remote object, but not in the interface, remain
invisible for the client.

## Registering the Service

Once we created the remote implementation, we need to bind the remote object to a RMI server.

### Creating a RMI Server

First, we need to create a drasyl node that contains a RMI server serving our remote object:

```java
// create server
final RmiServerHandler server = new RmiServerHandler();

// bootstrap node with server added to the pipeline
final ServerBootstrap b = new ServerBootstrap()
    .group(new NioEventLoopGroup())
    .channel(DrasylServerChannel.class)
    .handler(new TraversingDrasylServerChannelInitializer(identity) {
        @Override
        protected void initChannel(final DrasylServerChannel ch) {
            super.initChannel(ch);

            final ChannelPipeline p = ch.pipeline();

            p.addLast(new RmiCodec());
            p.addLast(server);
        }
    })
    .childHandler(/* code */);
```

### Binding the Remote Object

We can now create and bind our remote object to the RMI server. Each binding is identified by a unique
key.

```java
// create remote object
final MessengerService service = new MessengerServiceImpl();

// bind to server
server.bind("MessengerService", service);
```

## Creating the Client

Finally, let's write the client to invoke the remote object's methods.

```java
// create client
final RmiClientHandler client = new RmiClientHandler();

// bootstrap node with client added to the pipeline
final ServerBootstrap b = new ServerBootstrap()
    .group(new NioEventLoopGroup())
    .channel(DrasylServerChannel.class)
    .handler(new TraversingDrasylServerChannelInitializer(identity) {
        @Override
        protected void initChannel(final DrasylServerChannel ch) {
            super.initChannel(ch);

            final ChannelPipeline p = ch.pipeline();

            p.addLast(new RmiCodec());
            p.addLast(client);
        }
    })
    .childHandler(/* code */);
```

### Lookup for the Remote Object

We can now look up the remote object using the bounded unique key and the address of the RMI server's node.
And finally, we'll invoke the `sendMessage` method:

```java
// lookup
final MessengerService service = client.lookup("MessengerService", MessengerService.class, serverAddress);

// invoke
service.sendMessage("Client Message").addListener((FutureListener<String>) future -> {
    if (future.isSuccess()) {
        System.out.println("Succeeded: " + future.getNow());
    }
    else {
        System.err.println("Errored:");
        future.cause().printStackTrace();
    }
});
```

A fully working example can be found [here](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example/rmi).

## Further Reading

### Who Called Me?

You may be interested in getting to know who called you. For this, you must add a field of
type `DrasylAddress` and annotate it with `RmiCaller`. drasyl will then inject the current caller to
this variable before every invocation.

```java
public class MessengerServiceImpl implements MessengerService {
    @RmiCaller
    private DrasylAddress caller;

    @Override
    public Future<String> sendMessage(String clientMessage) {
        System.out.println("Called by: " + caller);
        /* code */
    }
}
```

Note to save the `caller` value when doing asynchronous operations. Otherwise, it might be possible
that a subsequent invocation has already changed the `caller` field.

### Adjust Timeout

By default, all invocations will timeout after 60 seconds by completing the future exceptionally with a `RmiException`.
But you can customize this value per class and method.
To do so, just add the annotation `RmiTimeout` to your implementation class or method.
A class annotation will override the default value, while a method annotation will override any class annotation.

```java
public class MessengerServiceImpl implements MessengerService {
    @RmiTimeout(5_000L)
    @Override
    public Future<String> sendMessage(String clientMessage) {
        /* code */
    }
}
```

---

_This page is an adapted version of the [Java RMI tutorial by Baeldung](https://www.baeldung.com/java-rmi)._
