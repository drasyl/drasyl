package org.drasyl.example.chord;

import io.netty.bootstrap.ServerBootstrap;
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
import org.drasyl.handler.dht.chord.ChordListener;
import org.drasyl.handler.dht.chord.task.ChordAskPredecessorTask;
import org.drasyl.handler.dht.chord.task.ChordFixFingersTask;
import org.drasyl.handler.dht.chord.task.ChordStabilizeTask;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({ "java:S106", "java:S110", "java:S2093" })
public class ChordNodeMulti {
    private static final String CONTACT = System.getProperty("contact", "d4cb81c941c276ccac03e7d7e1131e1d7f3d00454eb7ee578374b1cfc3990284");
    private static final int COUNT = Integer.parseInt(System.getProperty("count", "1000"));
    public static void main(final String[] args) throws IOException, InterruptedException {
        final EventLoopGroup group = new NioEventLoopGroup();
        final AtomicReference<IdentityPublicKey> contact = new AtomicReference<>(IdentityPublicKey.of(CONTACT));
        for (int i = 2; i < COUNT; i++) {
            final Identity identity = IdentityManager.readIdentityFile(Path.of("/root/Identities/drasyl-" + i + ".identity"));

            final ServerBootstrap b = new ServerBootstrap()
                    .group(group)
                    .channel(DrasylServerChannel.class)
                    .handler(new TraversingDrasylServerChannelInitializer(identity, 20000 + i) {
                        @Override
                        protected void initChannel(final DrasylServerChannel ch) {
                            super.initChannel(ch);

                            final ChordFingerTable fingerTable = new ChordFingerTable(identity.getAddress());

                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new ChordCodec());
                            p.addLast(new ChordStabilizeTask(fingerTable, 500));
                            p.addLast(new ChordFixFingersTask(fingerTable, 500));
                            p.addLast(new ChordAskPredecessorTask(fingerTable, 500));
                            p.addLast(new ChordListener(fingerTable));

                            p.addLast(new ChannelDuplexHandler() {
                                @Override
                                public void userEventTriggered(final ChannelHandlerContext ctx,
                                                               final Object evt) {
                                    ctx.fireUserEventTriggered(evt);
                                    if (evt instanceof AddPathAndSuperPeerEvent) {
                                        System.out.println(ctx.channel().localAddress());
                                        p.addAfter(p.context(ChordCodec.class).name(), null, new ChordJoinHandler(fingerTable, contact.get()));
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
            b.bind(identity.getAddress());

//            contact.set(identity.getIdentityPublicKey());

            Thread.sleep(20000);
        }

        while (true) {
            Thread.sleep(1000);
        }
    }
}
