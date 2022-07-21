package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.TraversingDrasylServerChannelInitializer;
import org.drasyl.handler.dht.chord.ChordCodec;
import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.dht.chord.ChordUtil;
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

        final long localId = ChordUtil.chordId(identity.getAddress());
        final AtomicReference<IdentityPublicKey> predecessor = new AtomicReference<>();
        final ChordFingerTable fingerTable = new ChordFingerTable(localId);
        System.out.println("My Address: " + identity.getAddress());
        System.out.println("My Id: " + ChordUtil.chordIdToTex(localId) + " (" + localId + ")");
        System.out.println(fingerTable);

        final EventLoopGroup group = new NioEventLoopGroup();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new TraversingDrasylServerChannelInitializer(identity, 0) {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        super.initChannel(ch);

                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new ChordCodec());
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
