package org.drasyl.example.cyclon;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.example.util.SuperPeers;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.handler.remote.OtherNetworkFilter;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.crypto.ProtocolArmHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CyclonMembershipManagement {
    public static final Scanner SCANNER = new Scanner(System.in, UTF_8);
    private static final String IDENTITY = System.getProperty("identity", "alice.identity");
    private static final int NETWORK_ID = 1;

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        final EventLoopGroup group = new NioEventLoopGroup();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new ChannelInitializer<DrasylServerChannel>() {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        // control plane channel
                        final ChannelPipeline p = ch.pipeline();

                        p.addLast(new UdpServer(22527));
                        p.addLast(new ByteToRemoteMessageCodec());
                        p.addLast(new OtherNetworkFilter(NETWORK_ID));
                        p.addLast(new InvalidProofOfWorkFilter());
                        p.addLast(new ProtocolArmHandler(identity, 100));
                        p.addLast(new TraversingInternetDiscoveryChildrenHandler(NETWORK_ID, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, 5_000, 30_000, 60_000, SuperPeers.SUPER_PEERS, 60_000, 100));
                        p.addLast(new ApplicationMessageToPayloadCodec(NETWORK_ID, identity.getIdentityPublicKey(), identity.getProofOfWork()));

                        //p.addLast(new CyclonShufflingClientHandler());
                    }
                })
                .childHandler(new ChannelInitializer<DrasylChannel>() {
                    @Override
                    protected void initChannel(final DrasylChannel ch) {
                        // data plane channel
                        final ChannelPipeline p = ch.pipeline();
                    }
                });

        try {
            final Channel ch = b.bind(identity.getAddress()).syncUninterruptibly().channel();

            final boolean keepRunning = true;
            while (keepRunning) {
                System.out.print("Space-separated list with peers to add: ");
                final String input = SCANNER.nextLine();
                final String[] words = input.split("\\s");
                final Set<IdentityPublicKey> addresses = new HashSet<>();
                for (int i = 0; i < words.length; i++) {
                    final String word = words[i];
                    try {
                        final IdentityPublicKey publicKey = IdentityPublicKey.of(word);
                        addresses.add(publicKey);
                    }
                    catch (final IllegalArgumentException e) {
                        System.err.println("Invalid public key discarded: " + word);
                        System.err.flush();
                    }
                }
                System.out.printf(addresses.size() + " address(es) added.");
            }

            //ch.closeFuture().awaitUninterruptibly();
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
