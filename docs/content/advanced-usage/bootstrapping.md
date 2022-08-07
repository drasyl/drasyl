# Bootstrapping

In the [previous section](../getting-started.md), we learned how to use the `DrasylNode` class — a
minimalist interface to integrate drasyl into your application.
It provides rich flexibility through customization, configuring the node that best fits your use case.
But for some use cases, you may want to customize your node even further.
For this, we provide a Bootstrapping interface, a more powerful way to create a drasyl node.

To help understand the Bootstrapping interface, we first want to give the background some information on
the technical internals of drasyl and the applied concepts.

## Background: Netty Concepts

The core of drasyl is built with [Netty](https://netty.io/).
Netty describes itself as "an asynchronous event-driven network application framework for rapid
development of maintainable high performance protocol servers & clients".
To this end, Netty defines an architectural model and a rich set of design patterns for network
programming.

### `Channel`s

One of Netty's primary building block is the `Channel`. A `Channel` is a [basic construct of Java NIO](https://docs.oracle.com/javase/7/docs/api/java/nio/channels/Channel.html).
It represents
> an open connection to an entity such as a hardware device, a file, a  network socket, or a program
> component that is capable of performing  one or more distinct I/O operations, for example reading
> or writing.

Netty uses this `Channel` interface mainly for IP-based transports like TCP or UDP.
drasyl has adopted this concept and provides a unified interface for communication with peers, regardless of their location and route.

### Events Handlers

Netty (and therefore drasyl) uses distinct events to notify your application about status changes of the channel or issues operations.
This allows your application to react with the appropriate action based on the type of occurring event (e.g., logging, data transformation, flow-control, business logic, etc.).

To control what actions your application should apply, each `Channel` applies the interceptor design pattern.
That means that you can register a given number of interceptors (so-called [`ChannelHandler`](https://livebook.manning.com/book/netty-in-action/chapter-6/)s) to a channel, performing independently various.
These `ChannelHandler`s can be added, removed, and resorted to at any time for each `Channel`.
Netty provides an extensive set of predefined handlers, most of which are compatible with drasyl!

### Bootstrapping

[Bootstrapping](https://livebook.manning.com/book/netty-in-action/chapter-8/) defines the startup code configuring the `Channel`.
At a minimum, it binds the node to a given overlay identity on which it will listen for connection requests.

To learn more about Netty, the used concepts, and how to use them, we recommend reading the
[Netty User Guide](https://netty.io/wiki/user-guide.html) as well as the book "Netty in Action" by
Marvin Wolfthal and Norman Maurer.

## Create Node using Bootstrapping

First, we need to create a `ServerBootstrap` object that describes the behavior of our drasyl node.
The `TraversingDrasylServerChannelInitializer` is a special `ChannelHandler` that conveniently
populates other handlers necessary for the minimal operation of a drasyl node.
The implementation of `ChannelInitializer` in the following line defines how to handle received data
from other peers. In this case, they are interpreted and output as a string.

```java
final ServerBootstrap b = new ServerBootstrap()
    // we want to create a drasyl-based channel (not UDP or TCP).
    .channel(DrasylServerChannel.class)
    // create and assign a thread pool dedicated to proccess in- and outbound data.
    .group(new NioEventLoopGroup())
    // ChannelHandler in charge of performing all control plane-related operations.
    // There is only one server channel per node.
    .handler(new TraversingDrasylServerChannelInitializer(identity))
    // ChannelHandler in charge of performing all data plane-related operations.
    // There is a child channel for each peer.
    .childHandler(new ChannelInitializer<DrasylChannel>() {
        @Override
        protected void initChannel(final DrasylChannel ch) {
            final ChannelPipeline p = ch.pipeline();

            p.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                @Override
                protected void channelRead0(final ChannelHandlerContext ctx,
                                            final ByteBuf msg) {
                    System.out.println("Got `" + msg.toString(UTF_8) + "` from `" + ctx.channel().remoteAddress() + "`");
                }
            });
        }
    });
```

Now that the "recipe" for our drasyl node has been defined, we can start it and wait for messages to
arrive.

```java
try {
    // create new node with given identity.
    final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();
    System.out.println("Node listening on address " + ch.localAddress());
    // wait for node to stop.
    ch.closeFuture().awaitUninterruptibly();
}
finally {
    // ensure that thread pool is shutdown
    group.shutdownGracefully();
}
```

A fully working example can be found
here: [EchoServerBootstrap](https://github.com/drasyl-overlay/drasyl/blob/master/drasyl-examples/src/main/java/org/drasyl/example/echo/EchoServerBootstrap.java)
