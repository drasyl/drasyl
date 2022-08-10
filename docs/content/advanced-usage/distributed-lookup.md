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

Below you see a bootstrap object that will either create a new circle, or if `contact` is set, tries to join an circle by contacting the given node:

```java
final long myId = chordId(identity.getAddress());
final ChordFingerTable fingerTable = new ChordFingerTable(identity.getAddress());
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

            // our chord implementation uses internally RMI
            p.addLast(new OverlayMessageToEnvelopeMessageCodec());
            p.addLast(new RmiCodec());
            final RmiClientHandler client = new RmiClientHandler();
            final RmiServerHandler server = new RmiServerHandler();
            p.addLast(client);
            p.addLast(server);
            
            final DefaultChordService defaultChordService = new DefaultChordService(fingerTable, client);
            server.bind(SERVICE_NAME, defaultChordService);

            // some chord housekeeping tasks
            p.addLast(new ChordStabilizeTask(fingerTable, 500, client, defaultChordService));
            p.addLast(new ChordFixFingersTask(fingerTable, 500, client, defaultChordService));
            p.addLast(new ChordAskPredecessorTask(fingerTable, 500, client));

            if (contact != null) {
                p.addLast(new ChannelDuplexHandler() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        ctx.fireUserEventTriggered(evt);
                        if (evt instanceof AddPathAndSuperPeerEvent) {
                            p.addAfter(p.context(ChordStabilizeTask.class).name(), null, new ChordJoinHandler(fingerTable, contact, client));
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

TODO



## Example

A fully working example can be found [here](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example/chord).
