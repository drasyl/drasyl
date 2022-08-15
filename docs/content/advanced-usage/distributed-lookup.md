# Distributed Lookup

On this page you will learn how to integrate a distributed lookup service for the node that stores a desired data item.
The feature is based on the Chord protocol that provides support for just one operation: given a key, it maps the key onto a node.

If you would like to learn mor about this protocl, please refer to this paper:
> I. Stoica et al., "Chord: a scalable peer-to-peer lookup protocol for Internet applications," in
> IEEE/ACM Transactions on Networking, vol. 11, no. 1, pp. 17-32, Feb. 2003.
> 
> [https://doi.org/10.1109/TNET.2002.808407](https://doi.org/10.1109/TNET.2002.808407)

To use this feature, you have to use the [bootstrapping interface](./bootstrapping.md), where you have to customize the server channel's ChannelInitializer.

## Establish Chord Circle

Chord constructs a distributed has table where each node is responsible for data items belonging to a partial keyspace.
To this end, all nodes are arranged in an ordered circle, where each node is equipped with a finger table that accelerates the traversal of the circle.

Below wee bootstrap a node that will either create a new circle, or if `contact` is set, tries to join an circle by contacting the given node:

```java
final long myId = chordId(identity.getAddress());
System.out.println("My Address : " + identity.getAddress());
System.out.println("My Id      : " + ChordUtil.chordIdHex(myId) + " (" + chordIdPosition(myId) + ")");
System.out.println();

final IdentityPublicKey contact = /* code */;

final ServerBootstrap b = new ServerBootstrap()
    .group(new NioEventLoopGroup())
    .channel(DrasylServerChannel.class)
    .handler(new TraversingDrasylServerChannelInitializer(identity) {
        @Override
        protected void initChannel(final DrasylServerChannel ch) {
            super.initChannel(ch);

            final ChannelPipeline p = ch.pipeline();

            // add RMI as our chord implementation relies on it
            p.addLast(new OverlayMessageToEnvelopeMessageCodec());
            p.addLast(new RmiCodec());
            final RmiClientHandler client = new RmiClientHandler();
            final RmiServerHandler server = new RmiServerHandler();
            p.addLast(client);
            p.addLast(server);

            // add chord            
            final LocalChordNode localService = new LocalChordNode(identity.getIdentityPublicKey(), client);
            server.bind(BIND_NAME, localService);
            p.addLast(new ChordHousekeepingHandler(localNode));

            if (contact != null) {
                p.addLast(new ChannelDuplexHandler() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        ctx.fireUserEventTriggered(evt);
                        if (evt instanceof AddPathAndSuperPeerEvent) {
                            p.addLast(new ChordJoinHandler(contact, localNode));
                            ctx.pipeline().remove(ctx.name());
                        }
                    }
                });
            }
        }
    })
    .childHandler(/* code */);
```

## Lookup Node

Now its time to do lookups on the previously created chord circle.
Below wee bootstrap a node that will perform a chord lookup:

```java
final long myId = chordId(identity.getAddress());
System.out.println("My Address : " + identity.getAddress());
System.out.println("My Id      : " + ChordUtil.chordIdHex(myId) + " (" + chordIdPosition(myId) + ")");
System.out.println();

final IdentityPublicKey contact = /* code */;

final ServerBootstrap b = new ServerBootstrap()
    .group(new NioEventLoopGroup())
    .channel(DrasylServerChannel.class)
    .handler(new TraversingDrasylServerChannelInitializer(identity) {
        @Override
        protected void initChannel(final DrasylServerChannel ch) {
            super.initChannel(ch);

            final ChannelPipeline p = ch.pipeline();

            // add RMI as our chord implementation relies on it
            p.addLast(new OverlayMessageToEnvelopeMessageCodec());
            p.addLast(new RmiCodec());
            final RmiClientHandler client = new RmiClientHandler();
            final RmiServerHandler server = new RmiServerHandler();
            p.addLast(client);
            p.addLast(server);

            // add chord            
            p.addLast(new ChordLookupHandler(client));

            p.addLast(new SimpleChannelInboundHandler<ChordResponse>() {
                @Override
                protected void channelRead0(final ChannelHandlerContext ctx,
                                            final ChordResponse msg) {
                    System.out.println("Hash " + ChordUtil.chordIdHex(msg.getId()) + " (" + chordIdPosition(msg.getId()) + ") belongs to node " + msg.getAddress() + " (" + chordIdPosition(msg.getAddress()) + ")");
                }
            });
        }
    })
    .childHandler(/* code */);
```

We can now write `ChordLookup` messages to the channel. A response of such a lookup will be indicated by a `ChordResponse` message.
Listen on the Future returned by `Channel#writeAndFlush` to get updates on the (sucessfull) completion of the lookup.

Here's an snippet for that:
```java
channel.write(ChordLookup.of(contact, ChordUtil.chordId("ubuntu.iso"))).addListener((ChannelFutureListener) future -> {
    if (future.cause() != null) {
        future.cause().printStackTrace();
    }
});
```

## Example

A fully working example can be found [here](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example/chord).
