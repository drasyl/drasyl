package org.drasyl.jtasklet.broker.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand.TaskletVm;
import org.drasyl.jtasklet.channel.AbstractChannelInitializer;
import org.drasyl.jtasklet.handler.PathEventsFilter;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class BrokerChannelInitializer extends AbstractChannelInitializer {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    @SuppressWarnings("java:S107")
    public BrokerChannelInitializer(final Identity identity,
                                    final InetSocketAddress bindAddress,
                                    final int networkId,
                                    final long onlineTimeoutMillis,
                                    final Map<IdentityPublicKey, InetSocketAddress> superPeers,
                                    final PrintStream out,
                                    final PrintStream err,
                                    final Worm<Integer> exitCode,
                                    final boolean protocolArmEnabled,
                                    final Map<IdentityPublicKey, TaskletVm> vms) {
        super(identity, bindAddress, networkId, onlineTimeoutMillis, superPeers, protocolArmEnabled);
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        super.initChannel(ch);

        ch.pipeline().addLast(
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) {
                        out.print("Start broker...");
                        ctx.fireChannelActive();
                    }

                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        if (evt instanceof AddPathAndSuperPeerEvent) {
                            // node is now online
                            out.println("online!");
                            out.println("----------------------------------------------------------------------------------------------");
                            out.println("Broker listening on address " + ch.localAddress());
                            out.println("----------------------------------------------------------------------------------------------");
                            ctx.pipeline().remove(this);
                        }
                        else {
                            ctx.fireUserEventTriggered(evt);
                        }
                    }
                },
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) {
                        ctx.executor().scheduleWithFixedDelay(this::printVms, 5_000L, 5_000L, MILLISECONDS);
                        ctx.fireChannelActive();
                    }

                    private void printVms() {
                        synchronized (vms) {
                            final StringBuilder builder = new StringBuilder();

                            // table header
                            final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now(), Clock.systemDefaultZone().getZone());
                            builder.append(String.format("Time: %-35s%n", RFC_1123_DATE_TIME.format(zonedDateTime)));
                            builder.append(String.format("%-64s  %9s  %5s  %6s  %9s  %6s%n", "Tasklet VM", "Heartbeat", "State", "Tasks#", "Benchmark", "Token"));

                            // table body
                            for (final Map.Entry<IdentityPublicKey, TaskletVm> entry : vms.entrySet()) {
                                final IdentityPublicKey address = entry.getKey();
                                final TaskletVm vm = entry.getValue();

                                // table row
                                builder.append(String.format(
                                        "%-64s  %7dms  %5s  %6d  %9d  %6s%n",
                                        address,
                                        vm.timeSinceLastHeartbeat(),
                                        vm.isStale() ? "Stale" : (vm.isBusy() ? "Busy" : "Idle"),
                                        vm.getComputations(),
                                        vm.getBenchmark(),
                                        vm.getToken()
                                ));
                            }

                            out.println(builder);
                        }
                    }
                },
                new SuperPeerTimeoutHandler(onlineTimeoutMillis),
                new PathEventsFilter(),
                new PrintAndExitOnExceptionHandler(err, exitCode)
        );
    }
}
