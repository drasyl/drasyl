package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.dht.chord.ChordCodec;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordJoinHandler;
import org.drasyl.handler.dht.chord.ChordUtil;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ChordDistributedHashTable {
    private static final String IDENTITY = System.getProperty("identity", "chord.identity");

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final long myId = ChordUtil.chordId(identity.getAddress());
        final AtomicReference<IdentityPublicKey> predecessor = new AtomicReference<>();
        final ChordFingerTable fingerTable = new ChordFingerTable(myId);
        System.out.println("My Address: " + identity.getAddress());
        System.out.println("My Id: " + ChordUtil.chordIdToTex(myId));
        System.out.println();
        System.out.println("My Predecessor: " + predecessor.get());
        System.out.println("My Successor: " + fingerTable.get(1));
        System.out.println();
        System.out.println(fingerTable);

        final IdentityPublicKey contact = args.length > 0 ? IdentityPublicKey.of(args[0]) : null;

        final EventLoopGroup group = new NioEventLoopGroup();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, 50000 + (int) (myId % 10000)) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new ChannelDuplexHandler() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                ctx.fireUserEventTriggered(evt);
                                if (evt instanceof AddPathAndSuperPeerEvent) {
                                    // die ganzen chord handler hängne nun hinter den handlern, die in die child pipeline-führen :(
                                    // kann man die child router handler sachen nicht alle in den tail/head context verschieben!?
                                    p.addLast(new ChordCodec());

                                    if (contact != null) {
                                        p.addLast(new ChordJoinHandler(myId, contact));
                                    }

                                    ctx.pipeline().remove(ctx.name());
                                }
                            }
                        });
                    }
                })
                .childHandler(new ChannelInitializer<DrasylChannel>() {
                    @Override
                    protected void initChannel(final DrasylChannel ch) {
                        // data plane channel
                        // do nothing
                    }
                });

        try {
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();

            group.scheduleAtFixedRate(() -> {
                //
            }, 5_000L, 10_000L, MILLISECONDS);

            ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
