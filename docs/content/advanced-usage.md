# Advanced Usage

In the [previous section](./getting-started.md), we learned how to use the `DrasylNode` class — a
straightforward way to integrate drasyl into your application.
For advanced users, however, we offer a more powerful interface, which we will present in this
section.

To help understand this interface, we first want to give background information on the technical
internals of drasyl and the used concepts.

## Background: Netty - The Foundation of drasyl

The core of drasyl is built on [Netty](https://netty.io/).
Netty describes itself as "an asynchronous event-driven network application framework for rapid
development of maintainable high performance protocol servers & clients".
To this end, Netty defines an architectural model and a rich set of design patterns for network
programming.

One of the core concepts of Netty poses the `Channel`:
> A Channel is a basic construct of Java NIO. It represents
> an open connection to an entity such as a hardware device, a file, a
> network socket, or a program component that is capable of performing
> one or more distinct I/O operations, for example reading or writing.

Netty focuses on TCP and UDP-based channels that connect or bind to a particular IP address.

Each `Channel` applies the interceptor design pattern.
That means for each incoming (inbound) and outgoing (outbound) data, any number of interceptors (
so-called
`ChannelHandler`s) can be registered, performing various independent tasks (e.g., a
codec, a given protocol, etc.).
These `ChannelHandler`s can be added, removed, and resorted to at any time individually for
each `Channel`.
The "recipe" for the handlers used in a `Channel` is described in a so-called `Bootstrap` object.

drasyl has adopted these concepts and can offer the same flexibility in network application
development as Netty.
In addition, many of the `ChannelHandler`s provided by Netty are compatible with drasyl!

To learn more about Netty, the used concepts, and how to use them, we recommend reading the
[Netty User Guide](https://netty.io/wiki/user-guide.html) as well as the book "Netty in Action" by
Marvin Wolfthal and Norman Maurer.

## Create Node using the `Bootstrap` Interface

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
