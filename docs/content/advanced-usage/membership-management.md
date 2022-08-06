# Membership Management

On this page we show you how to integrate the gossip-based membership management protocol CYCLON.
The CYCLON protocol generates a unstructured overlay sharing similarities with a random graph.
Beside membership management this protocol can be used for resource discovery (e.g., by performing a
random walk).

If you would like to learn mor about this protocl, please refer to this paper:
> Voulgaris, S., Gavidia, D. & van Steen, M. CYCLON: Inexpensive Membership Management for
> Unstructured P2P Overlays. J Netw Syst Manage 13, 197–217 (2005)
> . https://doi.org/10.1007/s10922-005-4441-x

To use this protocol, you have to use the [bootstrapping interface](./bootstrapping.md) were you have to customize the server channel's ChannelInitializer.
Below you find a code snippet with a customized initializer including the Chord related handlers.
The `CyclonView` object passed to the handlers contains the local (partial) view of the network.
By calling `view.getNeighbors()` you will get a list of currently known neighbors and their corresponding addresses.
Please refer to the above-mentioned paper for choosing proper view size and shuffle size values.

```java
int viewSize = 8; // maximum size of peers in own view
int shuffleSize = 4; // maximum number of peers to shuffle
int shuffleInterval = 10_000; // shuffle every 10 seconds
Set<DrasylAddress> initialNeighbors = Set.of(...);
final CyclonView view = CyclonView.ofKeys(viewSize, initialNeighbors);

final ServerBootstrap b = new ServerBootstrap()
    .group(new NioEventLoopGroup())
    .channel(DrasylServerChannel.class)
    .handler(new TraversingDrasylServerChannelInitializer(identity, 0) {
        @Override
        protected void initChannel(final DrasylServerChannel ch) {
            super.initChannel(ch);

            final ChannelPipeline p = ch.pipeline();

            // (de)serialize cyclon messages 
            p.addLast(new CyclonCodec());
            // requests shuffle with random neighbor
            p.addLast(new CyclonShufflingClientHandler(shuffleSize, shuffleInterval, view));
            // responses to shuffle requests
            p.addLast(new CyclonShufflingServerHandler(shuffleSize, view));
        }
    })
    .childHandler(...);
```
A fully working example can be found
here: [CyclonMembershipManagement](https://github.com/drasyl-overlay/drasyl/blob/master/drasyl-examples/src/main/java/org/drasyl/example/cyclon/CyclonMembershipManagement.java)
