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
import org.drasyl.handler.membership.cyclon.CyclonCodec;
import org.drasyl.handler.membership.cyclon.CyclonNeighbor;
import org.drasyl.handler.membership.cyclon.CyclonShufflingClientHandler;
import org.drasyl.handler.membership.cyclon.CyclonShufflingServerHandler;
import org.drasyl.handler.membership.cyclon.CyclonView;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * CYCLON Membership Management.
 *
 * @see <a href="https://doi.org/10.1007/s10922-005-4441-x">CYCLON: Inexpensive Membership
 * Management for Unstructured P2P Overlays</a>
 */
@SuppressWarnings("java:S106")
public class CyclonMembershipManagement {
    public static final Scanner SCANNER = new Scanner(System.in, UTF_8);
    private static final String IDENTITY = System.getProperty("identity", "cyclon.identity");
    private static final int VIEW_SIZE = Integer.parseInt(System.getProperty("viewSize", "4"));
    private static final int SHUFFLE_SIZE = Integer.parseInt(System.getProperty("shuffleSize", "2"));
    private static final int SHUFFLE_INTERVAL = Integer.parseInt(System.getProperty("viewSize", "10000"));
    private static final int NETWORK_ID = 1;
    public static final String PROMPT = "Space-separated list of peers to add manually to my view: ";

    public static void main(final String[] args) throws IOException {
        // load/create identity
        final File identityFile = new File(IDENTITY);
        if (!identityFile.exists()) {
            System.out.println("No identity present. Generate new one. This may take a while ...");
            IdentityManager.writeIdentityFile(identityFile.toPath(), Identity.generateIdentity());
        }
        final Identity identity = IdentityManager.readIdentityFile(identityFile.toPath());

        System.out.println("My address = " + identity.getAddress());

        final CyclonView view = CyclonView.ofKeys(VIEW_SIZE, Arrays.stream(args).map(IdentityPublicKey::of).collect(Collectors.toSet()));

        final EventLoopGroup group = new NioEventLoopGroup();
        final ServerBootstrap b = new ServerBootstrap()
                .group(group)
                .channel(DrasylServerChannel.class)
                .handler(new ChannelInitializer<DrasylServerChannel>() {
                    @Override
                    protected void initChannel(final DrasylServerChannel ch) {
                        // control plane channel
                        final ChannelPipeline p = ch.pipeline();

                        // default handlers
                        p.addLast(new UdpServer(0));
                        p.addLast(new ByteToRemoteMessageCodec());
                        p.addLast(new OtherNetworkFilter(NETWORK_ID));
                        p.addLast(new InvalidProofOfWorkFilter());
                        p.addLast(new ProtocolArmHandler(identity, 100));
                        p.addLast(new TraversingInternetDiscoveryChildrenHandler(NETWORK_ID, identity.getIdentityPublicKey(), identity.getIdentitySecretKey(), identity.getProofOfWork(), 0, 5_000, 30_000, 60_000, SuperPeers.SUPER_PEERS, 60_000, 100));
                        p.addLast(new ApplicationMessageToPayloadCodec(NETWORK_ID, identity.getIdentityPublicKey(), identity.getProofOfWork()));

                        // CYCLON handlers
                        p.addLast(new CyclonCodec());
                        p.addLast(new CyclonShufflingClientHandler(SHUFFLE_SIZE, SHUFFLE_INTERVAL, view));
                        p.addLast(new CyclonShufflingServerHandler(SHUFFLE_SIZE, view));
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
                System.out.print("\r");
                System.out.println("Current view: " + " ".repeat(PROMPT.length()));
                System.out.println(view);
                System.out.println();
                System.out.print(PROMPT);
            }, 5_000L, 10_000L, MILLISECONDS);

            final AtomicBoolean keepRunning = new AtomicBoolean(true);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                keepRunning.set(false);
                ch.close();
            }));
            while (keepRunning.get()) {
                System.out.print(PROMPT);
                final String input = SCANNER.nextLine();
                final String[] words = input.split("\\s");
                final Set<CyclonNeighbor> newNeighbors = new HashSet<>();
                for (final String word : words) {
                    if (word.isBlank()) {
                        continue;
                    }

                    try {
                        final IdentityPublicKey publicKey = IdentityPublicKey.of(word);
                        newNeighbors.add(CyclonNeighbor.of(publicKey));
                        view.add(CyclonNeighbor.of(publicKey));
                    }
                    catch (final IllegalArgumentException e) {
                        System.err.println("Invalid peer address discarded: " + word);
                        System.err.flush();
                    }
                }

                System.out.println(newNeighbors.size() + " address(es) added.");
            }

            ch.closeFuture().addListener(future -> keepRunning.set(false));
        }
        finally {
            group.shutdownGracefully();
        }
    }
}
